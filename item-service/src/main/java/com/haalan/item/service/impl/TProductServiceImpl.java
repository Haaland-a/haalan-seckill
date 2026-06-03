package com.haalan.item.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.item.domain.dto.ProductStringDTO;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.ProductDetailVO;
import com.haalan.item.domain.vo.StockVO;
import com.haalan.item.service.*;
import com.haalan.item.util.ItemBloomFilterUtil;
import com.haalan.item.util.ItemCacheEmptyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *
 * @author Haaland
 * @description TProductServiceImpl
 * </p>
 * @date 2026/4/17
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class TProductServiceImpl implements TProductService {

	private final ITSkuService skuService;
	private final ITCategoryService categoryService;
	private final ITBrandService brandService;
	private final ITSpuService spuService;
	private final StringRedisTemplate redisTemplate;
	private final ItemBloomFilterUtil itemBloomFilterUtil;
	private final ItemCacheEmptyUtil itemCacheEmptyUtil;

	@Override
	public ProductDetailVO getProductDetail(Long spuId) {
		// 先通过布隆过滤器快速判断，不在过滤器中则可能是冷启动，继续走缓存/DB兜底
		if (!itemBloomFilterUtil.mightContainSpu(spuId)) {
			log.warn("布隆过滤器判断SPU可能不存在(可能冷启动): {}", spuId);
		}

		String cacheKey = "product:detail:" + spuId;

		// 使用缓存空对象机制获取数据（内部会回填布隆过滤器）
		ProductDetailVO vo = itemCacheEmptyUtil.getOrCache(
				cacheKey,
				() -> queryProductDetailFromDb(spuId),
				ProductDetailVO.class
		);

		if (vo == null) {
			throw new RuntimeException("商品不存在");
		}

		return vo;
	}

	/**
	 * 从数据库查询商品详情
	 */
	private ProductDetailVO queryProductDetailFromDb(Long spuId) {
		long start = System.currentTimeMillis();
		TSpu spu = spuService.getById(spuId);
		if (spu == null) {
			return null;
		}
		long mid = System.currentTimeMillis();
		log.info("spu详情查询耗时: {}ms", mid - start);
		TCategory category = categoryService.getById(spu.getCategoryId());
		TBrand brand = brandService.getById(spu.getBrandId());

		List<TSku> skuList = skuService.lambdaQuery()
				.eq(TSku::getSpuId, spuId)
				.eq(TSku::getStatus, 1)
				.list();

		long end = System.currentTimeMillis();
		log.info("sku详情查询耗时: {}ms", end - mid);
		log.info("商品详情查询耗时: {}ms", end - start);
		ProductDetailVO vo = new ProductDetailVO();
		vo.setSpuId(spu.getId());
		vo.setSpuCode(spu.getSpuCode());
		vo.setSpuName(spu.getName());
		vo.setDescription(spu.getDescription());
		vo.setMainImage(spu.getMainImage());
		vo.setImages(StrUtil.isNotBlank(spu.getImages())
				? JSONUtil.toList(spu.getImages(), String.class)
				: Collections.emptyList());
		vo.setCategoryId(spu.getCategoryId());
		vo.setCategoryName(category != null ? category.getName() : "");
		vo.setBrandName(brand != null ? brand.getName() : "");

		List<ProductDetailVO.SkuDetailVO> skuDetailList = skuList.stream()
				.map(sku -> {
					ProductDetailVO.SkuDetailVO skuDetailVO = new ProductDetailVO.SkuDetailVO();
					skuDetailVO.setSkuId(sku.getId());
					skuDetailVO.setSkuCode(sku.getSkuCode());
					skuDetailVO.setSkuName(sku.getName());
					skuDetailVO.setPrice(sku.getPromotionPrice() != null
							? sku.getPromotionPrice()
							: sku.getPrice());
					skuDetailVO.setOriginalPrice(sku.getPrice());
					skuDetailVO.setStock((long) (sku.getStock() != null ? sku.getStock() : 0));
					skuDetailVO.setSoldCount(Long.valueOf(sku.getSoldCount() != null ? sku.getSoldCount() : 0));
					skuDetailVO.setStatus(sku.getStatus());
					skuDetailVO.setSpecifications(StrUtil.isNotBlank(sku.getSpecifications())
							? JSONUtil.toBean(sku.getSpecifications(), Map.class)
							: new HashMap<>());
					skuDetailVO.setImages(StrUtil.isNotBlank(sku.getImages())
							? JSONUtil.toList(sku.getImages(), String.class)
							: Collections.emptyList());
					return skuDetailVO;
				})
				.collect(Collectors.toList());

		vo.setSkuList(skuDetailList);

		// 将SPU和SKU ID添加到布隆过滤器
		itemBloomFilterUtil.addSpu(spu.getId());
		List<Long> skuIds = skuList.stream()
				.map(TSku::getId)
				.filter(id -> id != null)
				.collect(Collectors.toList());
		itemBloomFilterUtil.addSkus(skuIds);

		return vo;
	}

	@Override
	public StockVO getStockBySkuId(Long skuId) {
		String stock = redisTemplate.opsForValue().get("sku-stock:" + skuId);
		//状态
		String status = redisTemplate.opsForValue().get("sku-status:" + skuId);
		StockVO vo = new StockVO();
		if (stock != null) {

			TSku sku = skuService.getById(skuId);

			if (sku == null) {
				throw new RuntimeException("SKU不存在");
			}
			redisTemplate.opsForValue().set("sku-stock:" + skuId, sku.getStock().toString(), 2, TimeUnit.HOURS);
			redisTemplate.opsForValue().set("sku-status:" + skuId, sku.getStatus().toString(), 2, TimeUnit.HOURS);
			vo.setSkuId(sku.getId());
			vo.setStock(sku.getStock());
			vo.setStatus(sku.getStock() > 0 ? 1 : 0);
			vo.setStatusName(sku.getStock() > 0 ? "有货" : "缺货");
		} else {
			//缓存中没有,查询数据库
			TSku sku = skuService.getById(skuId);
			vo.setSkuId(skuId);
			vo.setStock(sku.getStock());
			vo.setStatus(sku.getStatus());
			vo.setStatusName(sku.getStock() > 0 ? "有货" : "缺货");
		}
		return vo;
	}

	@Override
	public ProductStringDTO getCode(Long skuId) {
		if (skuId == null) {
			throw new BizIllegalException("skuId不能为空");
		}
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在: " + skuId);
		}
		if (StrUtil.isBlank(sku.getSkuCode())) {
			throw new BizIllegalException("SKU编码不存在: " + skuId);
		}

		ProductStringDTO dto = new ProductStringDTO();
		dto.setSkuCode(sku.getSkuCode());
		dto.setProductName(sku.getName());
		dto.setOriginalPrice(sku.getPrice());
		dto.setStock(sku.getStock());
		return dto;
	}

	@Override
	public Boolean deductStock(Long skuId, Integer stock) {

		if (skuId == null) {
			throw new BizIllegalException("skuId不能为空");
		}
		if (stock == null) {
			throw new BizIllegalException("库存不能为空");
		}
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在: " + skuId);
		}
		if (sku.getStock() < stock) {
			throw new BizIllegalException("库存不足: " + skuId);
		}

		return skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.ge(TSku::getStock, stock)  //乐观锁1,防止多扣除
				.setSql("stock = stock - " + stock)
				.update();
	}

	@Override
	public Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId) {
		if (pIdToSId == null) {
			throw new BizIllegalException("参数不能为空");
		}
		Map<String, Map<String, String>> resultMap = new HashMap<>();
		for (SeckillProductSkuDTO dto : pIdToSId) {
			TSku tSku = skuService.getById(dto.getSkuId());
			Map<String, String> map = new HashMap<>();
			//需要传递的商品信息
			map.put("status", String.valueOf(tSku.getStatus()));
			map.put("images", tSku.getImages());
			map.put("soldCount", String.valueOf(tSku.getSoldCount()));
			map.put("specifications", String.valueOf(tSku.getSpecifications()));
			resultMap.put(
					dto.getId() + ":" + dto.getSkuId(),
					map
			);
		}
		return resultMap;
	}

	@Override
	public SkuDetailVO getSkuDetail(Long skuId) {
		// 先通过布隆过滤器快速判断，不在过滤器中则可能是冷启动，继续走缓存/DB兜底
		if (!itemBloomFilterUtil.mightContainSku(skuId)) {
			log.warn("布隆过滤器判断SKU可能不存在(可能冷启动): {}", skuId);
		}

		String cacheKey = "sku:detail:" + skuId;

		// 使用缓存空对象机制获取数据（内部会回填布隆过滤器）
		SkuDetailVO vo = itemCacheEmptyUtil.getOrCache(
				cacheKey,
				() -> querySkuDetailFromDb(skuId),
				SkuDetailVO.class
		);

		if (vo == null) {
			throw new RuntimeException("SKU不存在");
		}

		return vo;
	}

	/**
	 * 从数据库查询SKU详情
	 */
	private SkuDetailVO querySkuDetailFromDb(Long skuId) {
		TSku tSku = skuService.getById(skuId);
		if (tSku == null) {
			return null;
		}

		// 解析规格信息
		Map<String, String> specifications = new HashMap<>();
		if (StrUtil.isNotBlank(tSku.getSpecifications())) {
			specifications = JSONUtil.toBean(tSku.getSpecifications(), Map.class);
		}

		// 将SKU ID添加到布隆过滤器
		itemBloomFilterUtil.addSku(tSku.getId());

		return SkuDetailVO.builder()
				.skuId(tSku.getId())
				.spuId(tSku.getSpuId())
				.skuCode(tSku.getSkuCode())
				.skuName(tSku.getName())
				.images(tSku.getImages())
				.price(tSku.getPrice())
				.specifications(specifications)
				.stock(tSku.getStock())
				.status(tSku.getStatus())
				.build();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<BatchDeductStockResultVO> batchDeductStock(List<BatchDeductStockDTO> stockList) {
		if (stockList == null || stockList.isEmpty()) {
			return Collections.emptyList();
		}

		log.info("批量扣减库存开始, 数量: {}", stockList.size());
		long start = System.currentTimeMillis();

		// 提取所有SKU ID
		List<Long> skuIds = stockList.stream()
				.map(BatchDeductStockDTO::getSkuId)
				.collect(Collectors.toList());

		// 批量查询SKU信息
		List<TSku> skuList = skuService.listByIds(skuIds);
		Map<Long, TSku> skuMap = skuList.stream()
				.collect(Collectors.toMap(TSku::getId, sku -> sku));

		// 逐个扣减库存
		List<BatchDeductStockResultVO> results = stockList.stream()
				.map(dto -> {
					try {
						TSku sku = skuMap.get(dto.getSkuId());
						if (sku == null) {
							return BatchDeductStockResultVO.builder()
									.skuId(dto.getSkuId())
									.success(false)
									.failReason("SKU不存在")
									.build();
						}

						// 检查库存是否充足
						if (sku.getStock() < dto.getStock()) {
							return BatchDeductStockResultVO.builder()
									.skuId(dto.getSkuId())
									.success(false)
									.failReason("库存不足，当前库存: " + sku.getStock())
									.build();
						}

						// 使用乐观锁扣减库存
						boolean updated = skuService.lambdaUpdate()
								.eq(TSku::getId, dto.getSkuId())
								.ge(TSku::getStock, dto.getStock())
								.setSql("stock = stock - " + dto.getStock())
								.update();

						if (updated) {
							return BatchDeductStockResultVO.builder()
									.skuId(dto.getSkuId())
									.success(true)
									.build();
						} else {
							return BatchDeductStockResultVO.builder()
									.skuId(dto.getSkuId())
									.success(false)
									.failReason("扣减失败，可能库存已被其他请求扣减")
									.build();
						}
					} catch (Exception e) {
						log.error("扣减库存异常, skuId: {}", dto.getSkuId(), e);
						return BatchDeductStockResultVO.builder()
								.skuId(dto.getSkuId())
								.success(false)
								.failReason("系统异常: " + e.getMessage())
								.build();
					}
				})
				.collect(Collectors.toList());

		long end = System.currentTimeMillis();
		long successCount = results.stream().filter(BatchDeductStockResultVO::getSuccess).count();
		log.info("批量扣减库存完成, 总数: {}, 成功: {}, 失败: {}, 耗时: {}ms",
				stockList.size(), successCount, stockList.size() - successCount, end - start);

		return results;
	}

	@Override
	public Boolean addStock(Long skuId, Integer stock) {
		if (skuId == null) {
			throw new BizIllegalException("skuId不能为空");
		}
		if (stock == null || stock < 0) {
			throw new BizIllegalException("库存数量不合法");
		}
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在: " + skuId);
		}

		// 使用条件更新恢复库存，防止并发问题
		return skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.setSql("stock = stock + " + stock)
				.update();
	}
}
