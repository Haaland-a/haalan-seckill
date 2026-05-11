package com.haalan.api.client.fallback;

import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.ProductDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ItemServiceClientFallback implements FallbackFactory<ItemServiceClient> {

	@Override
	public ItemServiceClient create(Throwable cause) {
		return new ItemServiceClient() {
			//弃用
			@Override
			public ProductDetailVO getProductDetail(Long spuId) {
				log.error("调用 item-service 获取商品详情失败, spuId: {}", spuId, cause);
				//  返回兜底数据
				ProductDetailVO vo = new ProductDetailVO();
				vo.setSpuId(spuId);
				vo.setSpuName("商品信息获取失败");

				return vo;
			}

			@Override
			public ProductStringDTO getCode(Long skuId) {
				log.error("调用 item-service 获取商品详情失败, skuId: {}", skuId, cause);
				//  返回兜底数据
				ProductStringDTO dto = new ProductStringDTO();
				dto.setSkuCode("商品信息获取失败");
				dto.setProductName("商品信息获取失败");

				return dto;
			}

			@Override
			public Boolean deductStock(Long skuId, Integer stock) {

				log.error("调用 item-service 扣减库存失败, skuId: {}, num: {}", skuId, stock, cause);
				return false;
			}

			@Override
			public Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId) {
				log.error("调用 item-service 批量获取商品信息失败, pIdToSId: {}", pIdToSId, cause);
				return null;
			}

		};
	}
}
