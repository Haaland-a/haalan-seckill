package com.haalan.seckill.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
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
		tSeckillProduct.setAlipayProductCode(value.getAlipayProductCode());
		tSeckillProduct.setWechatProductCode(value.getWechatProductCode());

		//从sku中扣取库存到秒杀商品表中  先减再加
		itemServiceClient.deductStock(dto.getSkuId(), dto.getStock());
		boolean saved = this.save(tSeckillProduct);
		if (!saved) {
			throw new BizIllegalException("新增秒杀商品失败");
		}
		//获取新增的秒杀商品ID
		Long seckillProductId = tSeckillProduct.getId();
		if (seckillProductId == null) {
			throw new BizIllegalException("新增秒杀商品失败");
		}



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
				//如果都没问题，就先扣减库存再添加
			}

			TSeckillProduct product = new TSeckillProduct();
			BeanUtils.copyProperties(dto, product);
			product.setActivityId(activityId);
			product.setLockedStock(0);
			product.setSoldStock(0);
			product.setVersion(0);
			product.setSkuCode(productInfo.getSkuCode());
			product.setProductName(productInfo.getProductName());
			product.setAlipayProductCode(productInfo.getAlipayProductCode());
			product.setWechatProductCode(productInfo.getWechatProductCode());
			product.setOriginalPrice(
					productInfo.getOriginalPrice() != null ? productInfo.getOriginalPrice() : dto.getSeckillPrice()
			);

			toInsert.add(product);
		}
		//扣减库存 - 使用批量接口
		if (!toInsert.isEmpty()) {
			List<BatchDeductStockDTO> stockList = toInsert.stream()
					.map(product -> BatchDeductStockDTO.builder()
							.skuId(product.getSkuId())
							.stock(product.getStock())
							.build())
					.collect(Collectors.toList());

			List<BatchDeductStockResultVO> results = itemServiceClient.batchDeductStock(stockList);

			// 检查是否有失败的
			long failCount = results.stream().filter(r -> !r.getSuccess()).count();
			if (failCount > 0) {
				log.error("批量扣减库存部分失败, 失败数量: {}", failCount);
				// 可以选择抛出异常或记录日志
			}
		}
		int successCount = 0;
		//如果toInsert不为空
		if (!toInsert.isEmpty()) {
			boolean saved = this.saveBatch(toInsert);
			if (saved) {
				successCount = toInsert.size();
			} else {
				throw new BizIllegalException("批量添加秒杀商品失败");
			}
		}


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

	@Override
	public void deductStockAfterPayment(Long seckillProductId, Integer quantity) {
		if (seckillProductId == null) {
			log.error("秒杀商品ID不能为空");
			throw new BizIllegalException("秒杀商品ID不能为空");
		}
		if (quantity == null || quantity <= 0) {
			log.error("扣减数量必须大于0, seckillProductId={}, quantity={}", seckillProductId, quantity);
			throw new BizIllegalException("扣减数量必须大于0");
		}

		log.info("开始扣减秒杀商品数据库库存, seckillProductId={}, quantity={}", seckillProductId, quantity);

		// 使用乐观锁扣减库存
		boolean updated = this.lambdaUpdate()
				.eq(TSeckillProduct::getId, seckillProductId)
				.ge(TSeckillProduct::getStock, quantity)  // 确保库存充足
				.setSql("stock = stock - " + quantity)
				.setSql("sold_stock = sold_stock + " + quantity)
				.update();

		if (!updated) {
			log.error("扣减秒杀商品库存失败，可能库存不足或商品不存在, seckillProductId={}, quantity={}",
					seckillProductId, quantity);
			throw new BizIllegalException("扣减库存失败，可能库存不足");
		}

		log.info("秒杀商品数据库库存扣减成功, seckillProductId={}, quantity={}", seckillProductId, quantity);
	}

	@Override
	public void rollbackStockAfterRefund(Long seckillProductId, Integer quantity) {
		if (seckillProductId == null) {
			log.error("秒杀商品ID不能为空");
			throw new BizIllegalException("秒杀商品ID不能为空");
		}
		if (quantity == null || quantity <= 0) {
			log.error("回滚数量必须大于0, seckillProductId={}, quantity={}", seckillProductId, quantity);
			throw new BizIllegalException("回滚数量必须大于0");
		}

		log.info("开始回滚秒杀商品数据库库存, seckillProductId={}, quantity={}", seckillProductId, quantity);

		// 使用乐观锁回滚库存
		boolean updated = this.lambdaUpdate()
				.eq(TSeckillProduct::getId, seckillProductId)
				.setSql("stock = stock + " + quantity)
				.setSql("sold_stock = sold_stock - " + quantity)
				.update();

		if (!updated) {
			log.error("回滚秒杀商品库存失败，可能商品不存在, seckillProductId={}, quantity={}",
					seckillProductId, quantity);
			throw new BizIllegalException("回滚库存失败");
		}

		log.info("秒杀商品数据库库存回滚成功, seckillProductId={}, quantity={}", seckillProductId, quantity);
	}

}
