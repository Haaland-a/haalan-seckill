package com.haalan.seckill.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.config.SeckillConstants;
import com.haalan.seckill.domain.dto.SeckillActivityCreateDTO;
import com.haalan.seckill.domain.dto.SeckillActivityUpdateDTO;
import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.domain.vo.*;
import com.haalan.seckill.mapper.TSeckillActivityMapper;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.util.BloomFilterUtil;
import com.haalan.seckill.util.CacheEmptyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


/**
 * <p>
 * 秒杀活动表 服务实现类
 * </p>
 *
 * @author lyc
 * @since 2026-04-23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TSeckillActivityServiceImpl extends ServiceImpl<TSeckillActivityMapper, TSeckillActivity> implements ITSeckillActivityService {

	private final StringRedisTemplate redisTemplate;
	private final BloomFilterUtil bloomFilterUtil;
	private final CacheEmptyUtil cacheEmptyUtil;

	/**
	 * <p>
	 * 创建活动
	 * </p>
	 *
	 * @param dto
	 * @return SeckillActivityCreateResultVO
	 * @author Haaland
	 * @date 2026/4/23
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SeckillActivityCreateResultVO createActivity(SeckillActivityCreateDTO dto) {
		TSeckillActivity activity = getSecKillValue(dto);

		boolean saved = this.save(activity);
		if (!saved || activity.getId() == null) {
			throw new BizIllegalException("创建秒杀活动失败");
		}

		return SeckillActivityCreateResultVO.builder()
				.activityId(activity.getId())
				.build();
	}

	// 计算状态
	private Integer calculateStatus(LocalDateTime startTime, LocalDateTime endTime) {
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(startTime)) {
			return 0;
		}
		if (now.isAfter(endTime)) {
			return 2;
		}
		return 1;
	}

	/**
	 * <p>
	 * 修改活动内容
	 * </p>
	 *
	 * @param id
	 * @author Haaland
	 * @date 2026/4/23
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateActivity(Long id, SeckillActivityUpdateDTO dto) {
		TSeckillActivity tSeckillActivity = this.getById(id);
		if (tSeckillActivity == null) {
			throw new BizIllegalException("活动不存在");
		}

		if (dto.getActivityName() == null && dto.getEndTime() == null && dto.getStatus() == null) {
			throw new BizIllegalException("至少需要传入一个修改字段");
		}

		if (dto.getActivityName() != null) {
			if (!StringUtils.hasText(dto.getActivityName())) {
				throw new BizIllegalException("活动名称不能为空");
			}
			tSeckillActivity.setActivityName(dto.getActivityName());
		}

		if (dto.getEndTime() != null) {
			if (!tSeckillActivity.getStartTime().isBefore(dto.getEndTime())) {
				throw new BizIllegalException("结束时间必须晚于开始时间");
			}
			tSeckillActivity.setEndTime(dto.getEndTime());
		}

		if (dto.getStatus() != null) {
			tSeckillActivity.setStatus(dto.getStatus());
		}

		boolean update = this.updateById(tSeckillActivity);
		if (!update) {
			throw new BizIllegalException("修改秒杀活动失败");
		}
	}

	private TSeckillActivity getSecKillValue(SeckillActivityCreateDTO dto) {
		if (!dto.getStartTime().isBefore(dto.getEndTime())) {
			throw new BizIllegalException("开始时间必须早于结束时间");
		}

		TSeckillActivity activity = new TSeckillActivity();
		activity.setActivityName(dto.getActivityName());
		activity.setStartTime(dto.getStartTime());
		activity.setEndTime(dto.getEndTime());
		activity.setActivityDesc(dto.getActivityDesc());
		activity.setLimitPerUser(dto.getLimitPerUser());
		activity.setStatus(calculateStatus(dto.getStartTime(), dto.getEndTime()));//判断状态
		activity.setVersion(0);
		return activity;
	}

	/**
	 * <p>
	 * 活动回显
	 * </p>
	 *
	 * @param id
	 * @return SeckillActivityUpdateVO
	 * @author Haaland
	 * @date 2026/4/23
	 */
	@Override
	public SeckillActivityUpdateDTO echoActivity(Long id) {
		TSeckillActivity tSeckillActivity = this.getById(id);
		if (tSeckillActivity == null) {
			throw new BizIllegalException("活动不存在");
		}
		return SeckillActivityUpdateDTO.builder()
				.activityName(tSeckillActivity.getActivityName())
				.endTime(tSeckillActivity.getEndTime())
				.status(tSeckillActivity.getStatus())
				.build();
	}


	/**
	 * <p>
	 * 获取秒杀活动列表
	 * </p>
	 *
	 * @param status
	 * @return List<SeckillActivityCacheVO>
	 * @author Haaland
	 * @date 2026/4/23
	 */
	@Override
	public List<SeckillActivityCacheVO> getActivityList(Integer status) {
		// =========================
		// 查全部（null）→ 查 0/1/2 缓存
		// =========================
		if (status == null) {
			List<SeckillActivityCacheVO> result = new ArrayList<>();
			for (int s = 0; s <= 2; s++) {
				String key = SeckillConstants.SECKILL_ACTIVITY_CACHE_PREFIX + s;

				// 使用缓存空对象机制获取数据
				int finalS = s;
				List<SeckillActivityCacheVO> cacheList = cacheEmptyUtil.getOrCacheList(
						key,
						() -> queryFromDb(finalS),
						SeckillActivityCacheVO.class
				);

				if (cacheList != null && !cacheList.isEmpty()) {
					result.addAll(cacheList);
					// 将活动ID添加到布隆过滤器
					List<Long> activityIds = cacheList.stream()
							.map(SeckillActivityCacheVO::getActivityId)
							.filter(Objects::nonNull)
							.toList();
					bloomFilterUtil.addActivities(activityIds);
				}
			}
			return result;
		}

		// =========================
		// 进行中（status = 1）→ Redis Set索引
		// =========================
		if (status == 1) {
			String key = SeckillConstants.SECKILL_ACTIVITY_STATUS_INDEX + status;
			Set<String> ids = redisTemplate.opsForSet().members(key);

			List<SeckillActivityCacheVO> result = new ArrayList<>();

			if (ids != null && !ids.isEmpty()) {
				List<Long> fetchedIds = new ArrayList<>();
				for (String idStr : ids) {
					Long activityId = Long.valueOf(idStr);

					SeckillActivityCacheVO activity = getActivityFromCache(activityId);
					if (activity != null) {
						result.add(activity);
						fetchedIds.add(activityId);
					} else {
						// 缓存中没有，尝试从数据库查询并缓存
						String activityKey = SeckillConstants.SECKILL_ACTIVITY_LIST + activityId;
						SeckillActivityCacheVO dbActivity = cacheEmptyUtil.getOrCache(
								activityKey,
								() -> queryActivityById(activityId),
								SeckillActivityCacheVO.class
						);
						if (dbActivity != null) {
							result.add(dbActivity);
							fetchedIds.add(activityId);
						}
					}
				}
				if (!fetchedIds.isEmpty()) {
					bloomFilterUtil.addActivities(fetchedIds);
				}
			}

			if (result.isEmpty()) {
				log.warn("活动未查到");
				return Collections.emptyList();
			}

			result.sort(Comparator.comparing(
					SeckillActivityCacheVO::getActivityId,
					Comparator.nullsLast(Long::compareTo)
			).reversed());

			return result;
		}

		// =========================
		// 其他状态（0-未开始, 2-已结束）→ 直接查缓存
		// =========================
		String key = SeckillConstants.SECKILL_ACTIVITY_CACHE_PREFIX + status;

		// 使用缓存空对象机制获取数据
		List<SeckillActivityCacheVO> cacheList = cacheEmptyUtil.getOrCacheList(
				key,
				() -> queryFromDb(status),
				SeckillActivityCacheVO.class
		);

		// 将活动ID添加到布隆过滤器
		if (cacheList != null && !cacheList.isEmpty()) {
			List<Long> activityIds = cacheList.stream()
					.map(SeckillActivityCacheVO::getActivityId)
					.filter(Objects::nonNull)
					.toList();
			bloomFilterUtil.addActivities(activityIds);
		}

		return cacheList == null ? Collections.emptyList() : cacheList;
	}

	//从数据库中获取活动
	private List<SeckillActivityCacheVO> queryFromDb(Integer status) {
		List<TSeckillActivity> list = this.list(
				new QueryWrapper<TSeckillActivity>()
						.eq(status != null, "status", status)
						.orderByDesc("id")
		);

		List<SeckillActivityCacheVO> result = new ArrayList<>();

		for (TSeckillActivity t : list) {
			result.add(SeckillActivityCacheVO.builder()
					.activityId(t.getId())
					.activityName(t.getActivityName())
					.startTime(t.getStartTime())
					.endTime(t.getEndTime())
					.status(t.getStatus())
					.statusName(getStatusName(t.getStatus()))
					.activityDesc(t.getActivityDesc())
					.productCount(t.getTotalStock())
					.build());
		}

		return result;
	}

	// 从数据库中获取单个活动
	private SeckillActivityCacheVO queryActivityById(Long activityId) {
		TSeckillActivity activity = this.getById(activityId);
		if (activity == null) {
			return null;
		}

		return SeckillActivityCacheVO.builder()
				.activityId(activity.getId())
				.activityName(activity.getActivityName())
				.startTime(activity.getStartTime())
				.endTime(activity.getEndTime())
				.status(activity.getStatus())
				.statusName(getStatusName(activity.getStatus()))
				.activityDesc(activity.getActivityDesc())
				.productCount(activity.getTotalStock())
				.build();
	}


	private SeckillActivityCacheVO getActivityFromCache(Long activityId) {
		String activityInfoKey = SeckillConstants.SECKILL_ACTIVITY_LIST + activityId;

		try {
			Map<Object, Object> entries = redisTemplate.opsForHash().entries(activityInfoKey);

			if (entries == null || entries.isEmpty()) {
				return null;
			}
			// 由于redis中有缓存数据全部字段，所以需要转换成Map
			SeckillActivityCacheVO vo = BeanUtil.toBean(entries, SeckillActivityCacheVO.class);
			if (vo.getActivityId() == null) {
				log.warn("活动缓存数据异常, activityId={}", activityId);
				return null;
			}
			return vo;

		} catch (Exception e) {
			log.error("从Redis获取活动信息失败  activityId={} {}", e, activityId);
			return null;
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

	@Override
	public SeckillActivityDetailVO getActivityDetail(Long activityId) {
		String activityInfoKey = SeckillConstants.SECKILL_ACTIVITY_LIST + activityId;
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(activityInfoKey);
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		SeckillActivityCacheVO vo = BeanUtil.toBean(entries, SeckillActivityCacheVO.class);
		if (vo.getActivityId() == null) {
			log.warn("活动缓存数据异常, activityId={}", activityId);
			return null;
		}
		SeckillActivityDetailVO detailVO = BeanUtil.copyProperties(vo, SeckillActivityDetailVO.class);
		detailVO.setServerTime(LocalDateTime.now());
		String pKey = SeckillConstants.SECKILL_ACTIVITY_PRODUCTS + activityId;
		Set<String> productIds = redisTemplate.opsForSet().members(pKey);
		List<SeckillProductDetailVO> products = new ArrayList<>();
		if (productIds != null) {
			for (String productId : productIds) {

				Map<Object, Object> product = redisTemplate.opsForHash().entries(SeckillConstants.SECKILL_PRODUCT_PREFIX + productId);
				Object specifications = product.remove("specifications");
				SeckillProductDetailVO productVO = BeanUtil.toBean(
						product,
						SeckillProductDetailVO.class
				);
				productVO.setStock(redisTemplate.opsForValue().get(SeckillConstants.SECKILL_STOCK_PREFIX + productId));


				productVO.setLimitPerUser(redisTemplate.opsForValue().get(SeckillConstants.SECKILL_USER_LIMIT_PREFIX + productId));
				productVO.setImages(JSONUtil.toList(product.get("images").toString(), String.class));


				if (specifications != null) {
					productVO.setSpecifications(
							JSONUtil.toBean(
									specifications.toString(),
									new TypeReference<Map<String, String>>() {
									},
									false
							)
					);
				}
				//是否可以购买 需要获取用户的信息  但默认何以进入活动,所有人都ok
				productVO.setUserCanBuy(true);
				products.add(productVO);
			}
		}
		detailVO.setProducts(products);
		log.info("查询{}", detailVO.getActivityId());

		return detailVO;
	}

	@Override
	public SeckillProductInfoVO getProductDetail(Long seckillProductId, Long activityId) {
		if (seckillProductId == null) {
			throw new BizIllegalException("秒杀商品ID不能为空");
		}

		// 先通过布隆过滤器快速判断，不在过滤器中则可能是未预热
		if (!bloomFilterUtil.mightContainProduct(seckillProductId)) {
			log.warn("布隆过滤器判断秒杀商品可能不存在(可能未预热): {}", seckillProductId);
		}

		// 直接从 Redis Hash 读取（预热时写入的格式），内部会回填布隆过滤器
		SeckillProductInfoVO productVO = queryProductDetailFromDb(seckillProductId, activityId);

		if (productVO == null) {
			log.warn("秒杀商品不存在或缓存未预热, seckillProductId={}", seckillProductId);
			throw new BizIllegalException("秒杀商品不存在或尚未预热");
		}

		log.info("查询秒杀商品详情成功, seckillProductId={}", seckillProductId);
		return productVO;
	}

	@Override
	public List<SeckillActivityBriefVO> getAllActivities() {
		List<TSeckillActivity> list = this.list(
				new QueryWrapper<TSeckillActivity>().orderByDesc("create_time"));
		return list.stream().map(a -> {
			SeckillActivityBriefVO vo = new SeckillActivityBriefVO();
			vo.setActivityId(a.getId());
			vo.setActivityName(a.getActivityName());
			vo.setStatus(a.getStatus());
			vo.setStartTime(a.getStartTime());
			vo.setEndTime(a.getEndTime());
			vo.setTotalStock(a.getTotalStock());
			return vo;
		}).collect(Collectors.toList());
	}

	/**
	 * 从数据库查询秒杀商品详情
	 */
	private SeckillProductInfoVO queryProductDetailFromDb(Long seckillProductId, Long activityId) {
		String productKey = SeckillConstants.SECKILL_PRODUCT_PREFIX + activityId + ":" + seckillProductId;
		Map<Object, Object> productData = redisTemplate.opsForHash().entries(productKey);

		if (productData == null || productData.isEmpty()) {
			return null;
		}

		Object specificationsObj = productData.remove("specifications");

		SeckillProductInfoVO productVO = new SeckillProductInfoVO();
		productVO.setSeckillProductId(seckillProductId);
		productVO.setActivityId(activityId);

		if (productData.get("skuId") != null) {
			try {
				productVO.setSkuId(Long.parseLong(productData.get("skuId").toString()));
			} catch (NumberFormatException e) {
				log.warn("解析SKU ID失败", e);
			}
		}

		if (productData.get("name") != null) {
			productVO.setProductName(productData.get("name").toString());
		}

		if (productData.get("originalPrice") != null) {
			try {
				productVO.setOriginalPrice(new BigDecimal(productData.get("originalPrice").toString()));
			} catch (NumberFormatException e) {
				log.warn("解析原价失败", e);
			}
		}

		if (productData.get("seckillPrice") != null) {
			try {
				productVO.setSeckillPrice(new BigDecimal(productData.get("seckillPrice").toString()));
			} catch (NumberFormatException e) {
				log.warn("解析秒杀价失败", e);
			}
		}
		// 限购数量
		productVO.setLimitPerUser(redisTemplate.opsForValue().get(SeckillConstants.SECKILL_USER_LIMIT_PREFIX + activityId + ":" + seckillProductId));
		// 库存
		String stockStr = redisTemplate.opsForValue().get(SeckillConstants.SECKILL_STOCK_PREFIX + activityId + ":" + seckillProductId);
		if (stockStr != null) {
			try {
				productVO.setStock(Integer.parseInt(stockStr));
			} catch (NumberFormatException e) {
				log.warn("库存格式错误, seckillProductId={}, stock={}", seckillProductId, stockStr);
				productVO.setStock(0);
			}
		} else {
			productVO.setStock(0);
		}

		if (productData.get("images") != null) {
			try {
				productVO.setProductImages(JSONUtil.toList(productData.get("images").toString(), String.class));
			} catch (Exception e) {
				log.warn("解析商品图片失败, seckillProductId={}", seckillProductId);
				productVO.setProductImages(new ArrayList<>());
			}
		}

		if (specificationsObj != null) {
			try {
				productVO.setSpecifications(
						JSONUtil.toBean(
								specificationsObj.toString(),
								new TypeReference<Map<String, String>>() {
								},
								false
						)
				);
			} catch (Exception e) {
				log.warn("解析商品规格失败, seckillProductId={}", seckillProductId);
				productVO.setSpecifications(new HashMap<>());
			}
		}

		LocalDateTime startTime = null;
		LocalDateTime endTime = null;

		if (productData.get("startTime") != null) {
			try {
				startTime = LocalDateTime.parse(productData.get("startTime").toString());
				productVO.setStartTime(startTime);
			} catch (Exception e) {
				log.warn("解析开始时间失败", e);
			}
		}

		if (productData.get("endTime") != null) {
			try {
				endTime = LocalDateTime.parse(productData.get("endTime").toString());
				productVO.setEndTime(endTime);
			} catch (Exception e) {
				log.warn("解析结束时间失败", e);
			}
		}

		if (startTime != null && endTime != null) {
			LocalDateTime now = LocalDateTime.now();
			if (now.isBefore(startTime)) {
				productVO.setStatus(0);
				long countdown = java.time.Duration.between(now, startTime).getSeconds();
				productVO.setCountdown(countdown > 0 ? countdown : 0);
			} else if (now.isAfter(endTime)) {
				productVO.setStatus(2);
				productVO.setCountdown(0L);
			} else {
				productVO.setStatus(1);
				long countdown = java.time.Duration.between(now, endTime).getSeconds();
				productVO.setCountdown(countdown > 0 ? countdown : 0);
			}
		} else {
			productVO.setStatus(-1);
			productVO.setCountdown(0L);
		}
		// 用户已购买数量 只获取当前活动中的
		Object o = redisTemplate.opsForHash().get(SeckillConstants.SECKILL_USER_BUY_PREFIX + UserContext.getUser(), String.valueOf(seckillProductId));
		productVO.setUserPurchaseCount(o != null ? Integer.parseInt(o.toString()) : 0);

		// 将秒杀商品ID添加到布隆过滤器
		bloomFilterUtil.addProduct(seckillProductId);

		log.info("查询秒杀商品详情成功, seckillProductId={}", seckillProductId);
		return productVO;
	}
}
