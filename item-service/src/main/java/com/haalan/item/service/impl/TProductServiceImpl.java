package com.haalan.item.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TProductServiceImpl implements TProductService {

	private final ITSpuService spuService;
	private final ITSkuService skuService;
	private final ITCategoryService categoryService;
	private final ITBrandService brandService;
	private final ItemBloomFilterUtil itemBloomFilterUtil;
	private final ItemCacheEmptyUtil itemCacheEmptyUtil;

	@Override
	public ProductDetailVO getProductDetail(Long spuId) {
		if (!itemBloomFilterUtil.mightContainSpu(spuId)) {
			log.warn("布隆过滤器判断SPU可能不存在(可能冷启动): {}", spuId);
		}

		String cacheKey = "product:detail:" + spuId;

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

	@Override
	public ProductDetailVO getAdminProductDetail(Long spuId) {
		TSpu spu = spuService.getById(spuId);
		if (spu == null) {
			return null;
		}
		TCategory category = categoryService.getById(spu.getCategoryId());
		TBrand brand = brandService.getById(spu.getBrandId());

		// 管理端：查询所有 SKU，不过滤状态
		List<TSku> skuList = skuService.lambdaQuery()
				.eq(TSku::getSpuId, spuId)
				.list();

		ProductDetailVO vo = buildProductDetailVO(spu, skuList, category, brand);
		log.info("管理端查询商品详情成功, spuId={}", spuId);
		return vo;
	}

	/**
	 * 从数据库查询商品详情（用户端，仅上架SKU）
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

		ProductDetailVO vo = buildProductDetailVO(spu, skuList, category, brand);

		// 将SPU和SKU ID添加到布隆过滤器
		itemBloomFilterUtil.addSpu(spu.getId());
		List<Long> skuIds = skuList.stream()
				.map(TSku::getId)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		itemBloomFilterUtil.addSkus(skuIds);

		return vo;
	}

	private ProductDetailVO buildProductDetailVO(TSpu spu, List<TSku> skuList, TCategory category, TBrand brand) {
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
		vo.setBrandId(spu.getBrandId());
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
		return vo;
	}

	@Override
	public StockVO getStockBySkuId(Long skuId) {
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new RuntimeException("SKU不存在");
		}
		StockVO stockVO = new StockVO();
		stockVO.setSkuId(sku.getId());
		stockVO.setStock(sku.getStock());
		return stockVO;
	}

	@Override
	public ProductStringDTO getCode(Long skuId) {
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			return null;
		}
		TSpu spu = spuService.getById(sku.getSpuId());

		return ProductStringDTO.builder()
				.skuCode(sku.getSkuCode())
				.productName(sku.getName())
				.stock(sku.getStock())
				.originalPrice(sku.getPrice())
				.alipayProductCode(sku.getAlipayProductCode())
				.build();
	}

	@Override
	public Boolean deductStock(Long skuId, Integer stock) {
		if (skuId == null) {
			throw new RuntimeException("skuId不能为空");
		}
		if (stock == null) {
			throw new RuntimeException("库存不能为空");
		}
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new RuntimeException("SKU不存在: " + skuId);
		}
		if (sku.getStock() < stock) {
			throw new RuntimeException("库存不足: " + skuId);
		}

		boolean updated = skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.ge(TSku::getStock, stock)
				.setSql("stock = stock - " + stock)
				.update();

		if (updated) {
			// 库存在下单/取消/退款时都会变动，缓存不清会让搜索页和详情页长期显示旧库存。
			// 顺序必须是「先更新 DB 再删缓存」：反过来并发读可能拿到旧值回填缓存，造成长期脏数据。
			itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
					Collections.singletonList(skuId));
		}
		return updated;
	}

	@Override
	public Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId) {
		if (pIdToSId == null || pIdToSId.isEmpty()) {
			return Collections.emptyMap();
		}

		// 收集所有 skuId
		List<Long> skuIds = pIdToSId.stream()
				.map(SeckillProductSkuDTO::getSkuId)
				.collect(Collectors.toList());

		// 批量查询 SKU
		List<TSku> skuList = skuService.lambdaQuery()
				.in(TSku::getId, skuIds)
				.list();

		Map<Long, TSku> skuMap = skuList.stream()
				.collect(Collectors.toMap(TSku::getId, s -> s, (a, b) -> a));

		// key = "seckillProductId:skuId"  →  images + specifications
		Map<String, Map<String, String>> result = new HashMap<>(pIdToSId.size());
		for (SeckillProductSkuDTO item : pIdToSId) {
			TSku sku = skuMap.get(item.getSkuId());
			if (sku == null) {
				log.warn("batchGetProductInfo: SKU不存在, skuId={}", item.getSkuId());
				continue;
			}
			Map<String, String> info = new HashMap<>();
			info.put("images", sku.getImages() != null ? sku.getImages() : "[]");
			info.put("specifications", sku.getSpecifications() != null ? sku.getSpecifications() : "{}");
			result.put(item.getId() + ":" + item.getSkuId(), info);
		}

		log.info("batchGetProductInfo: 成功获取 {} 个SKU信息", result.size());
		return result;
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

	@Override
	public List<SkuDetailVO> getSkuDetailBatch(List<Long> skuIds) {
		if (skuIds == null || skuIds.isEmpty()) {
			return Collections.emptyList();
		}
		// 去重，避免重复 SKU 造成无效查询
		List<Long> distinctIds = skuIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
		if (distinctIds.isEmpty()) {
			return Collections.emptyList();
		}
		// 一次 SQL 批量查库，避免购物车结算时 N 次远程调用
		List<TSku> skus = skuService.listByIds(distinctIds);
		if (skus == null || skus.isEmpty()) {
			return Collections.emptyList();
		}
		return skus.stream().map(this::toSkuDetailVO).collect(Collectors.toList());
	}

	/**
	 * TSku -> SkuDetailVO 转换（避免类型转换问题，手动赋值）
	 */
	private SkuDetailVO toSkuDetailVO(TSku sku) {
		SkuDetailVO vo = new SkuDetailVO();
		vo.setSkuId(sku.getId());
		vo.setSkuCode(sku.getSkuCode());
		vo.setSkuName(sku.getName());
		vo.setSpuId(sku.getSpuId());
		vo.setSpecifications(sku.getSpecifications());
		vo.setPrice(sku.getPrice());
		vo.setStock(sku.getStock());
		// 销量：搜索页的「销量」列和 sales_desc 排序都依赖这个字段，
		// 漏了它 search-service 侧永远只能拿到 0。
		vo.setSoldCount(sku.getSoldCount());
		vo.setImages(sku.getImages());
		vo.setStatus(sku.getStatus());
		return vo;
	}

	/**
	 * 从数据库查询SKU详情
	 */
	private SkuDetailVO querySkuDetailFromDb(Long skuId) {
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			return null;
		}
		return toSkuDetailVO(sku);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<BatchDeductStockResultVO> batchDeductStock(List<BatchDeductStockDTO> stockList) {
		if (stockList == null || stockList.isEmpty()) {
			return Collections.emptyList();
		}

		List<BatchDeductStockResultVO> results = new ArrayList<>(stockList.size());
		for (BatchDeductStockDTO item : stockList) {
			Long skuId = item.getSkuId();
			Integer stock = item.getStock();
			if (skuId == null) {
				results.add(BatchDeductStockResultVO.builder()
						.skuId(null).success(false).failReason("skuId不能为空").build());
				continue;
			}
			if (stock == null || stock <= 0) {
				results.add(BatchDeductStockResultVO.builder()
						.skuId(skuId).success(false).failReason("扣减数量必须大于0").build());
				continue;
			}

			try {
				boolean ok = deductStock(skuId, stock);
				if (ok) {
					results.add(BatchDeductStockResultVO.builder()
							.skuId(skuId).success(true).build());
				} else {
					// 乐观锁未命中：并发下库存被其他事务先行扣减
					results.add(BatchDeductStockResultVO.builder()
							.skuId(skuId).success(false).failReason("库存不足或并发冲突").build());
				}
			} catch (Exception e) {
				log.error("批量扣减库存异常, skuId={}, stock={}", skuId, stock, e);
				results.add(BatchDeductStockResultVO.builder()
						.skuId(skuId).success(false).failReason(e.getMessage()).build());
			}
		}

		// 任一条失败则整体回滚，保证普通下单「扣库存 + 落订单」原子性
		boolean anyFail = results.stream().anyMatch(r -> !Boolean.TRUE.equals(r.getSuccess()));
		if (anyFail) {
			log.warn("批量扣减库存存在失败项，整体回滚: {}", results);
			throw new RuntimeException("批量扣减库存失败");
		}
		return results;
	}

	@Override
	public Boolean addStock(Long skuId, Integer stock) {
		if (skuId == null) {
			throw new RuntimeException("skuId不能为空");
		}
		if (stock == null || stock <= 0) {
			throw new RuntimeException("恢复库存数量必须大于0");
		}

		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			throw new RuntimeException("SKU不存在: " + skuId);
		}

		boolean updated = skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.setSql("stock = stock + " + stock)
				.update();

		if (updated) {
			// 取消订单/退款/管理员补货都会走这里，同样必须失效库存与详情缓存，
			// 否则用户端会一直看到「已售罄」或与实际不符的库存数。
			itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
					Collections.singletonList(skuId));
		}
		return updated;
	}
}
