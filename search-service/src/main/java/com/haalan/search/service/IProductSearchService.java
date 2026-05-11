package com.haalan.search.service;


import com.haalan.common.domain.PageDTO;
import com.haalan.search.domain.dto.ProductSearchDTO;
import com.haalan.search.domain.vo.ProductSearchVO;

/**
 * <p>
 * 商品搜索服务类
 * </p>
 *
 * @author lyc
 * @since 2026-04-16
 */
public interface IProductSearchService {

	/**
	 * <p>
	 * 搜索商品列表
	 * </p>
	 *
	 * @param searchDTO 搜索参数
	 * @return PageDTO<ProductSearchVO> 分页搜索结果
	 * @author Haaland
	 * @date 2026/4/16
	 */
	PageDTO<ProductSearchVO> searchProducts(ProductSearchDTO searchDTO);

//	ProductDetailVO getProductDetail(Long spuId);
}
