package com.haalan.item.controller;


import com.haalan.common.domain.PageDTO;
import com.haalan.common.domain.R;
import com.haalan.item.domain.dto.SpuCreateDTO;
import com.haalan.item.domain.dto.SpuQueryDTO;
import com.haalan.item.domain.vo.SpuCreateResultVO;
import com.haalan.item.domain.vo.SpuListVO;
import com.haalan.item.service.ITSpuService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
}
