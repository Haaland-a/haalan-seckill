package com.haalan.api.client;

import com.haalan.api.client.fallback.ItemServiceClientFallback;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@FeignClient(value = "item-service", fallbackFactory = ItemServiceClientFallback.class)
public interface ItemServiceClient {
	//不要的
//	@GetMapping("/api/sku/inner/detail/{spuId}")
//	ProductDetailVO getProductDetail(@PathVariable("spuId") Long spuId);

	@GetMapping("/api/product/inner/getCode")
	ProductStringDTO getCode(@RequestParam("skuId") Long skuId);


	@GetMapping("/api/product/inner/deductStock")
	Boolean deductStock(@RequestParam("skuId") @NotNull(message = "商品SKU ID不能为空") Long skuId,
						@RequestParam("stock") @NotNull(message = "库存不能为空") Integer stock);


	@PostMapping("/api/product/inner/getProductInfo")
	Map<String, Map<String, String>> batchGetProductInfo(
			@RequestBody List<SeckillProductSkuDTO> pIdToSId);

	@GetMapping("/api/product/inner/getSkuDetail")
	SkuDetailVO getSkuDetail(@RequestParam("skuId") Long skuId);

	/**
	 * 批量查询 SKU 详情（购物车结算用，避免 N 次远程调用）
	 *
	 * @param skuIds SKU ID 列表
	 * @return SKU 详情列表
	 */
	@PostMapping("/api/product/inner/getSkuDetailBatch")
	List<SkuDetailVO> getSkuDetailBatch(@RequestBody List<Long> skuIds);

	/**
	 * 批量扣减库存
	 *
	 * @param stockList 扣减库存列表
	 * @return 扣减结果列表
	 */
	@PostMapping("/api/product/inner/batchDeductStock")
	List<BatchDeductStockResultVO> batchDeductStock(@RequestBody List<BatchDeductStockDTO> stockList);

	/**
	 * 恢复库存（取消订单时使用）
	 *
	 * @param skuId SKU ID
	 * @param stock 恢复数量
	 * @return 是否成功
	 */
	@GetMapping("/api/product/inner/addStock")
	Boolean addStock(@RequestParam("skuId") @NotNull(message = "商品SKU ID不能为空") Long skuId,
					 @RequestParam("stock") @NotNull(message = "库存数量不能为空") Integer stock);

	/**
	 * 累加 SKU 销量（支付成功后调用）
	 *
	 * @param skuId SKU ID
	 * @param num   增加数量
	 * @return 累加后的销量
	 */
	@GetMapping("/api/product/inner/incrSoldCount")
	Integer incrSoldCount(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num);
}
