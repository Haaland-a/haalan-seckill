package com.haalan.search.controller;


import com.haalan.common.domain.PageDTO;
import com.haalan.common.domain.R;
import com.haalan.search.domain.dto.ProductSearchDTO;
import com.haalan.search.domain.vo.ProductSearchVO;
import com.haalan.search.service.IProductSearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 商品搜索控制器
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Slf4j
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Api(tags = "商品搜索接口")
public class ProductSearchController {

	private final IProductSearchService productSearchService;

	/**
	 * <p>
	 * 搜索商品列表
	 * </p>
	 *
	 * @param searchDTO 搜索参数
	 * @return R<PageDTO<ProductSearchVO>> 分页搜索结果
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@GetMapping("/search")
	@ApiOperation("搜索商品列表")
	public R<PageDTO<ProductSearchVO>> searchProducts(@Validated ProductSearchDTO searchDTO) {
		log.info("搜索商品, 参数: {}", searchDTO);
		return R.success(productSearchService.searchProducts(searchDTO));
	}

//	@GetMapping("/detail/{spuId}")
//	@ApiOperation("商品详情")
//	public R<ProductDetailVO> getProductDetail(@PathVariable Long spuId) {
//		log.info("获取商品详情, 参数: {}", spuId);
//		return R.success(productSearchService.getProductDetail(spuId));
//	}
}
