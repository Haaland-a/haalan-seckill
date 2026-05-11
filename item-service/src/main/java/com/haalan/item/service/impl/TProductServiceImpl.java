package com.haalan.item.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.item.domain.dto.ProductStringDTO;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.ProductDetailVO;
import com.haalan.item.domain.vo.StockVO;
import com.haalan.item.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

	@Override
	public ProductDetailVO getProductDetail(Long spuId) {
		long start = System.currentTimeMillis();
		TSpu spu = spuService.getById(spuId);
		if (spu == null) {
			throw new RuntimeException("商品不存在");
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
			vo.setSkuId(skuId);
			vo.setStock(Integer.valueOf(stock));
			vo.setStatus(Integer.valueOf(status));
			vo.setStatusName(Integer.valueOf(stock) > 0 ? "有货" : "缺货");
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
}
