package com.haalan.item.service;

import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
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

	ProductDetailVO getAdminProductDetail(Long spuId);

	StockVO getStockBySkuId(Long skuId);

	ProductStringDTO getCode(Long skuId);

	Boolean deductStock(Long skuId, Integer stock);

	Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId);

	SkuDetailVO getSkuDetail(Long skuId);

	/**
	 * 批量查询SKU详情（购物车结算用，避免 N 次远程调用）
	 *
	 * @param skuIds SKU ID 列表
	 * @return SKU 详情列表（不存在的 SKU 会被跳过）
	 */
	List<SkuDetailVO> getSkuDetailBatch(List<Long> skuIds);

	/**
	 * 批量扣减库存（乐观锁，任一失败整体回滚）
	 *
	 * @param stockList 扣减库存列表
	 * @return 扣减结果列表
	 */
	List<BatchDeductStockResultVO> batchDeductStock(List<BatchDeductStockDTO> stockList);

	/**
	 * 恢复库存（取消订单时回滚库存）
	 *
	 * @param skuId SKU ID
	 * @param stock 恢复数量
	 * @return 是否成功
	 */
	Boolean addStock(Long skuId, Integer stock);
}
