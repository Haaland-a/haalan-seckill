package com.haalan.item.controller;


import com.haalan.common.domain.R;
import com.haalan.item.domain.vo.CategoryVO;
import com.haalan.item.service.ITCategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
@Api(tags = "商品分类管理")
public class TCategoryController {

	private final ITCategoryService categoryService;

	@GetMapping("/tree")
	@ApiOperation("获取分类树")
	public R<List<CategoryVO>> getCategoryTree(
			@RequestParam(required = false, defaultValue = "0") Long id,
			@RequestParam(required = false, defaultValue = "3") Integer level) {
		return R.success(categoryService.getCategoryTree(id, level));
	}
}
