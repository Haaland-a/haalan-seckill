package com.haalan.seckill.service.impl;

import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.seckill.cache.LocalSeckillCache;
import com.haalan.seckill.config.SeckillConstants;
import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.domain.po.TSeckillProduct;
import com.haalan.seckill.domain.vo.SeckillActivityPreheatVO;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.service.ITSeckillPreheatService;
import com.haalan.seckill.service.ITSeckillProductService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀活动预热服务实现
 * <p>
 * 预热职责：
 * 1. Redis 缓存：库存 String、限购 Hash、活动状态、时间窗口、就绪标记、幂等标记
 * 2. 本地缓存：售罄标记（快速拦截无效请求，不扣减库存）
 * 3. 动态 TTL：根据活动剩余时间计算，避免残留垃圾数据或提前过期
 * 4. 幂等保护：已预热的活动非强制不重复预热
 * 5. 降级容错：Redis 异常不阻塞本地预热 + 正常返回
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TSeckillPreheatServiceImpl implements ITSeckillPreheatService {


	private final ITSeckillActivityService seckillActivityService;
	private final ITSeckillProductService seckillProductService;
	private final StringRedisTemplate redisTemplate;
	private final LocalSeckillCache localCache;
	private final ItemServiceClient itemServiceClient;

	@Override
	@GlobalTransactional
	public SeckillActivityPreheatVO preheatActivity(Long activityId, boolean force) {
		// 1. 校验活动是否存在
		TSeckillActivity activity = seckillActivityService.getById(activityId);
		if (activity == null) {
			throw new BizIllegalException("活动不存在");
		}

		// 2. 已结束的活动不允许预热
		if (activity.getStatus() != null && activity.getStatus() == 2) {
			throw new BizIllegalException("活动已结束，无法预热");
		}

		// 3. 幂等检查：已预热 + 非强制 → 直接返回
		boolean alreadyPreheated = isAlreadyPreheated(activityId);
		if (alreadyPreheated && !force) {
			log.info("活动 {} 已预热过，跳过（force=false），直接返回当前状态", activityId);
			return buildAlreadyPreheatedResult(activity, activityId);
		}

		// 4. 如果强制重新预热，先清理旧数据
		if (alreadyPreheated && force) {
			log.info("活动 {} 强制重新预热，清理已有缓存", activityId);
			clearExistingCache(activityId);
		}

		// 5. 查询活动下的所有秒杀商品
		List<TSeckillProduct> products = seckillProductService.lambdaQuery()
				.eq(TSeckillProduct::getActivityId, activityId)
				.list();

		if (products == null || products.isEmpty()) {
			throw new BizIllegalException("该活动下暂无商品，无法预热");
		}

		int totalStock = products.stream().mapToInt(TSeckillProduct::getStock).sum();

		// 6. 计算动态 TTL
		long ttlSeconds = calculateTtl(activity);

		// 7. 预热到 Redis（核心：库存 String + 限购 Hash）
		boolean redisPreheated = preheatToRedis(activity, products, ttlSeconds);

		// 8. 预热到本地 JVM（只做售罄标记）
		boolean localPreheated = preheatToLocal(products);

		// 9. 构建返回结果
		return SeckillActivityPreheatVO.builder()
				.activityId(activityId)
				.productCount(products.size())
				.totalStock(totalStock)
				.preheatTime(LocalDateTime.now())
				.alreadyPreheated(false)
				.redisPreheated(redisPreheated)
				.localCachePreheated(localPreheated)
				.ttlSeconds(ttlSeconds)
				.activityStatus(activity.getStatus())
				.build();
	}

	// ==================== 私有方法 ====================

	/**
	 * 预热到 Redis
	 */
	private boolean preheatToRedis(TSeckillActivity activity, List<TSeckillProduct> products, long ttlSeconds) {
		try {
			Long activityId = activity.getId();
			//获取商品静态数据
			List<SeckillProductSkuDTO> pIdToSId = new ArrayList<>();
			for (TSeckillProduct product : products)
				pIdToSId.add(SeckillProductSkuDTO.builder()
						.id(product.getId())
						.skuId(product.getSkuId())
						.build());


			//秒杀id对应商品详情
			Map<String, Map<String, String>> productInfoMap = itemServiceClient.batchGetProductInfo(pIdToSId);  //批量获取商品信息

			// 商品级：库存 + 用户限购 Hash（限购按 skuId 维度）
			for (TSeckillProduct product : products) {
				String stockKey = SeckillConstants.SECKILL_STOCK_PREFIX + activityId + ":" + product.getId();

				String userLimitKey = SeckillConstants.SECKILL_USER_LIMIT_PREFIX + activityId + ":" + product.getId();
				String productKey = SeckillConstants.SECKILL_PRODUCT_PREFIX + activityId + ":" + product.getId();

				// 商品静态数据
				String key = product.getId() + ":" + product.getSkuId();

				Map<String, String> info = productInfoMap.get(key);

				info.put("productId", product.getId().toString());
				info.put("skuId", product.getSkuId().toString());
				info.put("skuCode", product.getSkuCode());
				info.put("name", product.getProductName());
				info.put("originalPrice", product.getOriginalPrice().toString());
				info.put("seckillPrice", product.getSeckillPrice().toString());
				//先不写库存
				info.put("soldStock", product.getSoldStock().toString());
				info.put("alipayProductCode", product.getAlipayProductCode());
				info.put("wechatProductCode", product.getWechatProductCode());
				info.put("startTime", activity.getStartTime().toString());
				info.put("endTime", activity.getEndTime().toString());
				log.info("商品静态数据: {}", info);
				if (info != null) {
					redisTemplate.opsForHash().putAll(productKey, info);
					log.info("商品静态数据保存成功: {}", redisTemplate.opsForHash().get(productKey, "id"));
				}
				// 活动商品索引
				String activityProductKey =
						SeckillConstants.SECKILL_ACTIVITY_PRODUCTS + activityId;

				// 保存活动对应商品的key
				String aps = activityId + ":" + product.getId();
				redisTemplate.opsForSet().add(activityProductKey, aps);
				redisTemplate.expire(activityProductKey, (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);

				redisTemplate.expire(productKey, (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);
				// 商品库存
				redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()), (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);
				// 用户限购商品级限购
				redisTemplate.opsForValue().set(userLimitKey, String.valueOf(product.getLimitPerUser()), (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);

			}

			// 活动级：用户总限购
			if (activity.getLimitPerUser() != null && activity.getLimitPerUser() > 0) {
				String activityLimitKey = SeckillConstants.SECKILL_ACTIVITY_USER_LIMIT_PREFIX + activityId;
				redisTemplate.opsForValue().set(activityLimitKey, String.valueOf(activity.getLimitPerUser()), (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);
			}

			// 活动状态
			int status = activity.getStatus() != null ? activity.getStatus()
					: calculateStatus(activity.getStartTime(), activity.getEndTime());
//            redisTemplate.opsForValue().set(
//                  SeckillConstants.SECKILL_ACTIVITY_STATUS+activityId,
//                    String.valueOf(status), (long) (ttlSeconds+Math.random()*60), TimeUnit.SECONDS);
//
//            // 时间窗口
//            if (activity.getStartTime() != null) {
//                redisTemplate.opsForValue().set(
//                        SeckillConstants.SECKILL_ACTIVITY_START+activityId,
//                        activity.getStartTime().toString(), (long) (ttlSeconds+Math.random()*60), TimeUnit.SECONDS);
//            }
//            if (activity.getEndTime() != null) {
//                redisTemplate.opsForValue().set(
//                        SeckillConstants.SECKILL_ACTIVITY_END+activityId,
//                        activity.getEndTime().toString(), (long) (ttlSeconds+Math.random()*60), TimeUnit.SECONDS);
//            }

			// 就绪标记
			redisTemplate.opsForValue().set(
					SeckillConstants.SECKILL_READY + activityId, "1", (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);
			cacheActivityInfo(activity, products.size(), status, (long) (ttlSeconds + Math.random() * 60));
			// 预热完成标记
			redisTemplate.opsForValue().set(SeckillConstants.SECKILL_PREHEAT_FLAG + activityId,
					String.valueOf(System.currentTimeMillis()), (long) (ttlSeconds + Math.random() * 60), TimeUnit.SECONDS);


			log.info("活动 {} Redis 预热完成，{} 件商品，总库存 {}，TTL {}s加60秒浮动",
					activityId, products.size(),
					products.stream().mapToInt(TSeckillProduct::getStock).sum(), ttlSeconds);
			return true;

		} catch (Exception e) {
			log.error("活动 {} Redis 预热异常，降级为仅本地预热", activity.getId(), e);
			return false;
		}
	}

	private void cacheActivityInfo(TSeckillActivity activity, Integer productCount, Integer status, long ttlSeconds) {
		try {
			String activityInfoKey = SeckillConstants.SECKILL_ACTIVITY_LIST + activity.getId();

			Map<String, String> activityInfo = new HashMap<>();
			activityInfo.put("activityId", String.valueOf(activity.getId()));
			activityInfo.put("activityName", activity.getActivityName());
			activityInfo.put("startTime", activity.getStartTime() != null ? activity.getStartTime().toString() : "");
			activityInfo.put("endTime", activity.getEndTime() != null ? activity.getEndTime().toString() : "");
			activityInfo.put("status", String.valueOf(status));
			activityInfo.put("statusName", getStatusName(status));
			activityInfo.put("activityDesc", activity.getActivityDesc() != null ? activity.getActivityDesc() : "");
			activityInfo.put("productCount", String.valueOf(productCount));

			redisTemplate.opsForHash().putAll(activityInfoKey, activityInfo);
			redisTemplate.expire(activityInfoKey, ttlSeconds, TimeUnit.SECONDS);

			addToStatusIndex(status, activity.getId(), ttlSeconds);

			log.debug("活动 {} 基本信息已缓存到Redis", activity.getId());
		} catch (Exception e) {
			log.warn("活动 {} 基本信息缓存失败", activity.getId(), e);
		}
	}

	private void addToStatusIndex(Integer status, Long activityId, long ttlSeconds) {
		try {
			String statusIndexKey = SeckillConstants.SECKILL_ACTIVITY_STATUS_INDEX + status;
			redisTemplate.opsForSet().add(statusIndexKey, String.valueOf(activityId));
			redisTemplate.expire(statusIndexKey, ttlSeconds, TimeUnit.SECONDS);
		} catch (Exception e) {
			log.warn("活动状态索引添加失败", e);
		}
	}

	private String getStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		return switch (status) {
			case 0 -> "未开始";
			case 1 -> "进行中";
			case 2 -> "已结束";
			default -> "未知";
		};
	}

	/**
	 * 预热到本地 JVM 缓存（只做售罄标记）
	 */
	private boolean preheatToLocal(List<TSeckillProduct> products) {
		try {
			for (TSeckillProduct product : products) {
				localCache.preheatSoldOutStatus(product.getId(), product.getStock());
			}
			log.info("本地售罄标记初始化完成，{} 件商品", products.size());
			return true;
		} catch (Exception e) {
			log.error("本地缓存预热异常", e);
			return false;
		}
	}

	private boolean isAlreadyPreheated(Long activityId) {
		String preheatFlagKey = SeckillConstants.SECKILL_PREHEAT_FLAG + activityId;
		return Boolean.TRUE.equals(redisTemplate.hasKey(preheatFlagKey));
	}

	private void clearExistingCache(Long activityId) {
		try {
			List<TSeckillProduct> existingProducts = seckillProductService.lambdaQuery()
					.eq(TSeckillProduct::getActivityId, activityId)
					.list();

			Set<Long> productIds = new HashSet<>();
			for (TSeckillProduct product : existingProducts) {
				productIds.add(product.getId());
				redisTemplate.delete(SeckillConstants.SECKILL_STOCK_PREFIX +
						activityId + ":" +
						product.getSkuId());

				redisTemplate.delete(SeckillConstants.SECKILL_USER_LIMIT_PREFIX + activityId + ":" + product.getSkuId());
			}

			redisTemplate.delete(SeckillConstants.SECKILL_ACTIVITY_USER_LIMIT_PREFIX + activityId);
//            redisTemplate.delete(SeckillConstants.SECKILL_ACTIVITY_STATUS+ activityId);
//            redisTemplate.delete(SeckillConstants.SECKILL_ACTIVITY_START+ activityId);
//            redisTemplate.delete(SeckillConstants.SECKILL_ACTIVITY_END+ activityId);
			redisTemplate.delete(SeckillConstants.SECKILL_PREHEAT_FLAG + activityId);
			redisTemplate.delete(SeckillConstants.SECKILL_READY + activityId);

			localCache.clearActivity(productIds);
			log.info("活动 {} 旧缓存已清理（{} 件商品）", activityId, existingProducts.size());
		} catch (Exception e) {
			log.warn("活动 {} 清理旧缓存异常", activityId, e);
		}
	}

	private long calculateTtl(TSeckillActivity activity) {
		LocalDateTime now = LocalDateTime.now();
		if (activity.getEndTime() != null && activity.getEndTime().isAfter(now)) {
			long seconds = Duration.between(now, activity.getEndTime()).getSeconds() + 3600; // 推迟 1 小时
			return Math.max(seconds, 300);
		}
		return 86400L;  //保底时间1天
	}

	private int calculateStatus(LocalDateTime startTime, LocalDateTime endTime) {
		LocalDateTime now = LocalDateTime.now();
		if (startTime != null && now.isBefore(startTime)) return 0;
		if (endTime != null && now.isAfter(endTime)) return 2;
		return 1;
	}

	private SeckillActivityPreheatVO buildAlreadyPreheatedResult(TSeckillActivity activity, Long activityId) {
		List<TSeckillProduct> products = seckillProductService.lambdaQuery()
				.eq(TSeckillProduct::getActivityId, activityId)
				.list();
		int totalStock = products.stream().mapToInt(TSeckillProduct::getStock).sum();

		String preheatFlagKey = SeckillConstants.SECKILL_PREHEAT_FLAG + activityId;
		Long ttl = redisTemplate.getExpire(preheatFlagKey, TimeUnit.SECONDS);

		return SeckillActivityPreheatVO.builder()
				.activityId(activityId)
				.productCount(products.size())
				.totalStock(totalStock)
				.preheatTime(LocalDateTime.now())
				.alreadyPreheated(true)
				.redisPreheated(true)
				.localCachePreheated(true)
				.ttlSeconds(ttl != null && ttl > 0 ? ttl : 0)
				.activityStatus(activity.getStatus())
				.build();
	}
}