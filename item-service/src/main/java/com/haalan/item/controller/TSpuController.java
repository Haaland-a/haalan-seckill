package com.haalan.item.controller;


import com.haalan.common.domain.PageDTO;
import com.haalan.common.domain.R;
import com.haalan.item.domain.dto.SpuCreateDTO;
import com.haalan.item.domain.dto.SpuQueryDTO;
import com.haalan.item.domain.dto.SpuUpdateDTO;
import com.haalan.item.domain.vo.ProductDetailVO;
import com.haalan.item.domain.vo.SpuCreateResultVO;
import com.haalan.item.domain.vo.SpuListVO;
import com.haalan.item.service.ITSpuService;
import com.haalan.item.service.TProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 商品SPU表 前端控制器
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@RestController
@RequestMapping("/api/admin/spu")
@RequiredArgsConstructor
@Api(tags = "商品SPU管理（管理端）")
public class TSpuController {

	private final ITSpuService spuService;
	private final TProductService productService;

	@PostMapping
	@ApiOperation("创建SPU")
	public R<SpuCreateResultVO> createSpu(@RequestBody @Validated SpuCreateDTO dto) {
		return R.success("创建成功", spuService.createSpu(dto));
	}

	@GetMapping("/list")
	@ApiOperation("查询SPU列表")  //由于是管理端,直接从数据库查询
	public R<PageDTO<SpuListVO>> querySpuList(@Validated SpuQueryDTO queryDTO) {
		return R.success(spuService.querySpuList(queryDTO));
	}

	@GetMapping("/{spuId}/detail")
	@ApiOperation("管理端查询商品详情（含所有SKU，不过滤）")
	public R<ProductDetailVO> getAdminProductDetail(@PathVariable Long spuId) {
		ProductDetailVO vo = productService.getAdminProductDetail(spuId);
		if (vo == null) {
			return R.error("商品不存在");
		}
		return R.success(vo);
	}

	//todo 加到api文档
	@PutMapping("/{spuId}/status")
	@ApiOperation("更新SPU状态（上架/下架）")
	public R<Void> updateSpuStatus(@PathVariable Long spuId, @RequestBody Map<String, Integer> body) {
		Integer status = body.get("status");
		if (status == null || (status != 0 && status != 1)) {
			return R.error("状态值无效，必须为 0（下架）或 1（上架）");
		}
		spuService.updateSpuStatus(spuId, status);
		return R.success("操作成功");
	}

	@PutMapping("/{spuId}")
	@ApiOperation("修改SPU信息")
	public R<Void> updateSpu(@PathVariable Long spuId, @RequestBody @Validated SpuUpdateDTO dto) {
		spuService.updateSpu(spuId, dto);
		return R.success("修改成功");
	}
}
