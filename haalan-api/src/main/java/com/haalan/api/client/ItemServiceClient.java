package com.haalan.api.client;

import com.haalan.api.client.fallback.ItemServiceClientFallback;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
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
}
