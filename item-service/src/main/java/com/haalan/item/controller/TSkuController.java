package com.haalan.item.controller;


import com.haalan.common.domain.R;
import com.haalan.item.domain.dto.SkuCreateDTO;
import com.haalan.item.domain.dto.SkuStockUpdateDTO;
import com.haalan.item.domain.vo.SkuCreateResultVO;
import com.haalan.item.domain.vo.SkuStockUpdateResultVO;
import com.haalan.item.service.ITSkuService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 商品SKU表 前端控制器
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sku")
@RequiredArgsConstructor
public class TSkuController {
	private final ITSkuService skuService;

	@PostMapping
	public R<SkuCreateResultVO> createSku(@RequestBody @Validated SkuCreateDTO dto) {
		log.info("创建SKU {}", dto);
		return R.success("创建成功", skuService.createSku(dto));

	}

	@PutMapping("/{skuId}/stock")
	@ApiOperation("更新SKU库存")
	public R<SkuStockUpdateResultVO> updateStock(
			@ApiParam(value = "SKU ID", required = true) @PathVariable Long skuId,
			@RequestBody @Validated SkuStockUpdateDTO dto) {
		log.info("更新SKU库存, skuId: {}, dto: {}", skuId, dto);
		return R.success("更新成功", skuService.updateStock(skuId, dto));
	}
}
