package com.haalan.item.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
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
import org.springframework.stereotype.Service;

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

		return skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.ge(TSku::getStock, stock)
				.setSql("stock = stock - " + stock)
				.update();
	}

	@Override
	public Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId) {
		return Collections.emptyMap();
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
		TSku sku = skuService.getById(skuId);
		if (sku == null) {
			return null;
		}
		SkuDetailVO vo = new SkuDetailVO();
		// 手动赋值，避免类型转换问题
		vo.setSkuId(sku.getId());
		vo.setSkuCode(sku.getSkuCode());
		vo.setSkuName(sku.getName());
		vo.setSpuId(sku.getSpuId());
		vo.setSpecifications(sku.getSpecifications());
		vo.setPrice(sku.getPrice());
		vo.setStock(sku.getStock());
		vo.setImages(sku.getImages());
		vo.setStatus(sku.getStatus());
		return vo;
	}

	@Override
	public List<BatchDeductStockResultVO> batchDeductStock(List<BatchDeductStockDTO> stockList) {
		return Collections.emptyList();
	}

	@Override
	public Boolean addStock(Long skuId, Integer stock) {
		if (skuId == null) {
			throw new RuntimeException("skuId不能为空");
		}
		if (stock == null || stock <= 0) {
			throw new RuntimeException("恢复库存数量必须大于0");
		}
		return skuService.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.setSql("stock = stock + " + stock)
				.update();
	}
}
