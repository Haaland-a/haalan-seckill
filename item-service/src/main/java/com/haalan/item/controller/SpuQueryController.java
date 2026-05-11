package com.haalan.item.controller;

import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.common.domain.R;
import com.haalan.item.domain.dto.ProductStringDTO;
import com.haalan.item.domain.vo.ProductDetailVO;
import com.haalan.item.domain.vo.StockVO;
import com.haalan.item.service.TProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Api(tags = "商品SPU查询接口")
public class SpuQueryController {

	private final TProductService productService;

	@GetMapping("/inner/detail/{spuId}")
	@ApiOperation("获取商品详情")
	public ProductDetailVO getProductDetail(@PathVariable Long spuId) {
		return productService.getProductDetail(spuId);
	}

	@GetMapping("/detail/{spuId}")
	public R<ProductDetailVO> getProductDetailWrap(@PathVariable Long spuId) {
		return R.success(getProductDetail(spuId));
	}

	@GetMapping("/inner/stock/{skuId}")
	@ApiOperation("查询SKU库存（内部调用）")
	public StockVO getStock(@PathVariable Long skuId) {
		return productService.getStockBySkuId(skuId);
	}

	@GetMapping("/stock/{skuId}")
	@ApiOperation("查询SKU库存")
	public R<StockVO> getStockWrap(@PathVariable Long skuId) {
		return R.success(getStock(skuId));
	}

	@GetMapping("/inner/getCode")
	@ApiOperation("获取商品SKU编码")
	public ProductStringDTO getCode(@RequestParam Long skuId) {
		return productService.getCode(skuId);
	}

	@GetMapping("/inner/deductStock")
	@ApiOperation("修改库存")
	public Boolean deductStock(@RequestParam Long skuId, @RequestParam Integer stock) {
		return productService.deductStock(skuId, stock);
	}

	@PostMapping("/inner/getProductInfo")
	@ApiOperation("批量获取商品信息给预热")
		//可优化
	Map<String, Map<String, String>> batchGetProductInfo(
			@RequestBody List<SeckillProductSkuDTO> pIdToSId) {
		return productService.batchGetProductInfo(pIdToSId);
	}
}
