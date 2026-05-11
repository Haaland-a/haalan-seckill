package com.haalan.item.service;

import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.item.domain.dto.ProductStringDTO;
import com.haalan.item.domain.vo.ProductDetailVO;
import com.haalan.item.domain.vo.StockVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *
 * @author Haaland
 * @description TProductService
 * </p>
 * @date 2026/4/17
 */

public interface TProductService {

	ProductDetailVO getProductDetail(Long spuId);

	StockVO getStockBySkuId(Long skuId);

	ProductStringDTO getCode(Long skuId);

	Boolean deductStock(Long skuId, Integer stock);

	Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId);
}
