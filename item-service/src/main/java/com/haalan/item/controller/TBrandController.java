package com.haalan.item.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haalan.common.domain.R;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.service.ITBrandService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/brand")
@RequiredArgsConstructor
@Api(tags = "品牌管理（管理端）")
public class TBrandController {

	private final ITBrandService brandService;

	@GetMapping("/list")
	@ApiOperation("查询品牌列表（支持按名称搜索）")
	public R<List<TBrand>> getBrandList(@RequestParam(required = false) String keyword) {
		LambdaQueryWrapper<TBrand> wrapper = new LambdaQueryWrapper<TBrand>()
				.eq(TBrand::getStatus, 1)
				.like(keyword != null && !keyword.isEmpty(), TBrand::getName, keyword)
				.orderByAsc(TBrand::getId);
		return R.success(brandService.list(wrapper));
	}
}
