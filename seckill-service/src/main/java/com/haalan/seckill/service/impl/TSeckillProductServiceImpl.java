package com.haalan.seckill.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.utils.BeanUtils;
import com.haalan.seckill.domain.dto.SeckillActivityAddPDTO;
import com.haalan.seckill.domain.dto.SeckillProductStockUpdateDTO;
import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.domain.po.TSeckillProduct;
import com.haalan.seckill.domain.vo.SeckillActivityAddPVO;
import com.haalan.seckill.domain.vo.SeckillProductBatchAddResultVO;
import com.haalan.seckill.domain.vo.SeckillProductStockUpdateResultVO;
import com.haalan.seckill.mapper.TSeckillProductMapper;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.service.ITSeckillProductService;
import feign.FeignException;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *
 * @author Haaland
 * @description TSeckillProductServiceImpl
 * </p>
 * @date 2026/4/24
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class TSeckillProductServiceImpl extends ServiceImpl<TSeckillProductMapper, TSeckillProduct> implements ITSeckillProductService {

	private final ItemServiceClient itemServiceClient;
	private final ITSeckillActivityService seckillActivityService;
	private final StringRedisTemplate redisTemplate;

	/**
	 * 添加秒杀商品
	 *
	 * @param activityId
	 * @param dto
	 * @return
	 */
	@Override
	@GlobalTransactional
	public SeckillActivityAddPVO addProduct(Long activityId, SeckillActivityAddPDTO dto) {
		if (activityId == null) {
			throw new BizIllegalException("活动ID不能为空");
		}
		if (dto == null) {
			throw new BizIllegalException("添加商品不能为空");
		}
		if (dto.getSkuId() == null) {
			throw new BizIllegalException("商品SKU ID不能为空");
		}
		if (dto.getSeckillPrice() == null) {
			throw new BizIllegalException("秒杀价格不能为空");
		}
		if (dto.getStock() == null) {
			throw new BizIllegalException("库存不能为空");
		}
		if (dto.getLimitPerUser() == null) {
			throw new BizIllegalException("每人限购数量不能为空");
		}
		if (dto.getSort() == null) {
			throw new BizIllegalException("排序不能为空");
		}
		if (dto.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BizIllegalException("秒杀价格必须大于0");
		}
		if (dto.getStock() <= 0) {
			throw new BizIllegalException("库存必须大于0");
		}
		if (dto.getLimitPerUser() <= 0) {
			throw new BizIllegalException("每人限购数量必须大于0");
		}
		if (dto.getSort() < 0) {
			throw new BizIllegalException("排序不能小于0");
		}

		TSeckillActivity activity = seckillActivityService.getById(activityId);
		if (activity == null) {
			throw new BizIllegalException("活动不存在");
		}

		boolean exists = this.lambdaQuery()
				.eq(TSeckillProduct::getActivityId, activityId)
				.eq(TSeckillProduct::getSkuId, dto.getSkuId())
				.exists();
		// 判断商品是否已添加
		if (exists) {
			throw new BizIllegalException("该商品已添加到当前秒杀活动");
		}

		TSeckillProduct tSeckillProduct = new TSeckillProduct();
		BeanUtils.copyProperties(dto, tSeckillProduct);
		tSeckillProduct.setActivityId(activityId);
		tSeckillProduct.setLockedStock(0);
		tSeckillProduct.setSoldStock(0);
		tSeckillProduct.setVersion(0);


		//通过feign调用商品服务获取商品信息
		ProductStringDTO value;
		try {
			value = itemServiceClient.getCode(dto.getSkuId());
		} catch (FeignException e) {
			log.error("调用item-service获取SKU编码失败, skuId={}", dto.getSkuId(), e);
			throw new BizIllegalException("SKU不存在或商品服务不可用，请确认skuId后重试");
		}
		if (value == null) {
			throw new BizIllegalException("获取SKU信息失败，请确认skuId后重试");
		}
		if (value.getSkuCode() == null || value.getSkuCode().isBlank()) {
			throw new BizIllegalException("获取SKU编码失败，请确认skuId后重试");
		}
		if (value.getProductName() == null || value.getProductName().isBlank()) {
			throw new BizIllegalException("获取商品名称失败，请确认skuId后重试");
		}
		if (value.getStock() <= dto.getStock()) {
			throw new BizIllegalException("库存不足");
		}
		tSeckillProduct.setSkuCode(value.getSkuCode());
		tSeckillProduct.setProductName(value.getProductName());
		tSeckillProduct.setOriginalPrice(
				value.getOriginalPrice() != null ? value.getOriginalPrice() : dto.getSeckillPrice()
		);
		boolean saved = this.save(tSeckillProduct);
		if (!saved) {
			throw new BizIllegalException("新增秒杀商品失败");
		}
		//获取新增的秒杀商品ID
		Long seckillProductId = tSeckillProduct.getId();
		if (seckillProductId == null) {
			throw new BizIllegalException("新增秒杀商品失败");
		}
		//最后扣减库存
		//从sku中扣取库存到秒杀商品表中
		itemServiceClient.deductStock(dto.getSkuId(), dto.getStock());


		return SeckillActivityAddPVO.builder()
				.seckillProductId(seckillProductId)
				.build();
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	public SeckillProductBatchAddResultVO batchAddProducts(Long activityId, List<SeckillActivityAddPDTO> products) {
		if (activityId == null) {
			throw new BizIllegalException("活动ID不能为空");
		}
		if (products == null || products.isEmpty()) {
			throw new BizIllegalException("商品列表不能为空");
		}

		TSeckillActivity activity = seckillActivityService.getById(activityId);
		if (activity == null) {
			throw new BizIllegalException("活动不存在");
		}

		List<Long> skuIds = products.stream()
				.map(SeckillActivityAddPDTO::getSkuId)
				.distinct()
				.collect(Collectors.toList());
		//获取skuids中已存在的商品ID,
		Set<Long> existingSkuIds = this.lambdaQuery()
				.eq(TSeckillProduct::getActivityId, activityId)
				.in(TSeckillProduct::getSkuId, skuIds)
				.list()
				.stream()
				.map(TSeckillProduct::getSkuId)
				.collect(Collectors.toSet());

		Map<Long, ProductStringDTO> productInfoMap = batchGetProductInfo(skuIds);
		//批量获取商品信息
		List<TSeckillProduct> toInsert = new ArrayList<>();
		//失败列表
		List<SeckillProductBatchAddResultVO.FailedItem> failedList = new ArrayList<>();

		for (SeckillActivityAddPDTO dto : products) {
			Long skuId = dto.getSkuId();
			// 判断商品是否已添加
			if (existingSkuIds.contains(skuId)) {
				failedList.add(SeckillProductBatchAddResultVO.FailedItem.builder()
						.skuId(skuId)
						.reason("该商品已添加到当前秒杀活动")
						.build());
				continue;
			}
			// 通过feign调用商品服务获取商品信息
			ProductStringDTO productInfo = productInfoMap.get(skuId);
			if (productInfo == null) {
				failedList.add(SeckillProductBatchAddResultVO.FailedItem.builder()
						.skuId(skuId)
						.reason("SKU不存在或商品服务不可用")
						.build());
				continue;
			}
			// 获取SKU编码
			if (productInfo.getSkuCode() == null || productInfo.getSkuCode().isBlank()) {
				failedList.add(SeckillProductBatchAddResultVO.FailedItem.builder()
						.skuId(skuId)
						.reason("获取SKU编码失败")
						.build());
				continue;
			}
			// 获取商品名称
			if (productInfo.getProductName() == null || productInfo.getProductName().isBlank()) {
				failedList.add(SeckillProductBatchAddResultVO.FailedItem.builder()
						.skuId(skuId)
						.reason("获取商品名称失败")
						.build());
				continue;
			}
			if (productInfo.getStock() <= dto.getStock()) {
				failedList.add(SeckillProductBatchAddResultVO.FailedItem.builder()
						.skuId(skuId)
						.reason("商品库存不足")
						.build());
				continue;
			}

			TSeckillProduct product = new TSeckillProduct();
			BeanUtils.copyProperties(dto, product);
			product.setActivityId(activityId);
			product.setLockedStock(0);
			product.setSoldStock(0);
			product.setVersion(0);
			product.setSkuCode(productInfo.getSkuCode());
			product.setProductName(productInfo.getProductName());
			product.setOriginalPrice(
					productInfo.getOriginalPrice() != null ? productInfo.getOriginalPrice() : dto.getSeckillPrice()
			);

			toInsert.add(product);
		}

		int successCount = 0;
		if (!toInsert.isEmpty()) {
			boolean saved = this.saveBatch(toInsert);
			if (saved) {
				successCount = toInsert.size();
			} else {
				throw new BizIllegalException("批量添加秒杀商品失败");
			}
		}
		//扣减库存  //管理端写入操作少点，可以多次操作数据库扣减
		toInsert.forEach(product -> {
			itemServiceClient.deductStock(product.getSkuId(), product.getStock());
		});

		return SeckillProductBatchAddResultVO.builder()
				.successCount(successCount)
				.failedList(failedList)
				.build();
	}

	private Map<Long, ProductStringDTO> batchGetProductInfo(List<Long> skuIds) {
		Map<Long, ProductStringDTO> resultMap = new java.util.HashMap<>();
		for (Long skuId : skuIds) {
			try {
				ProductStringDTO productInfo = itemServiceClient.getCode(skuId);
				if (productInfo != null) {
					resultMap.put(skuId, productInfo);
				}
			} catch (FeignException e) {
				log.warn("获取SKU {} 信息失败", skuId, e);
			}
		}
		return resultMap;
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	public SeckillProductStockUpdateResultVO updateStock(Long seckillProductId, SeckillProductStockUpdateDTO dto) {
		if (seckillProductId == null) {
			throw new BizIllegalException("秒杀商品ID不能为空");
		}
		if (dto == null || dto.getStock() == null) {
			throw new BizIllegalException("库存数量不能为空");
		}

		TSeckillProduct product = this.getById(seckillProductId);
		if (product == null) {
			throw new BizIllegalException("秒杀商品不存在");
		}

		Integer newStock = dto.getStock();
		Integer oldStock = product.getStock();
		Integer stockDiff = newStock - oldStock;

		try {
			if (stockDiff != 0) {
				Boolean b = itemServiceClient.deductStock(product.getSkuId(), stockDiff);
			}
		} catch (Exception e) {
			throw new BizIllegalException("扣减库存失败");
		}

		product.setStock(newStock);
		boolean updated = this.lambdaUpdate()
				.eq(TSeckillProduct::getId, seckillProductId)
				.eq(TSeckillProduct::getVersion, product.getVersion())
				.set(TSeckillProduct::getStock, newStock)
				.set(TSeckillProduct::getVersion, product.getVersion() + 1)
				.update();   //利用乐观锁更新秒杀中的库存
		if (!updated) {
			throw new BizIllegalException("更新库存失败");
		}

		Boolean redisSynced = false;
		if (Boolean.TRUE.equals(dto.getSyncToRedis())) {
			String redisKey = "seckill:stock:" + seckillProductId;
			redisTemplate.opsForValue().set(redisKey, String.valueOf(newStock), 24, TimeUnit.HOURS);
			redisSynced = true;
		}

		return SeckillProductStockUpdateResultVO.builder()
				.seckillProductId(seckillProductId)
				.currentStock(newStock)
				.redisSynced(redisSynced)
				.build();
	}


}
