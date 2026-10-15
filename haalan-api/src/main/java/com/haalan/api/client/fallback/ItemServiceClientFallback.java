package com.haalan.api.client.fallback;

import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.dto.ProductStringDTO;
import com.haalan.api.domain.dto.SeckillProductSkuDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ItemServiceClientFallback implements FallbackFactory<ItemServiceClient> {

	@Override
	public ItemServiceClient create(Throwable cause) {
		return new ItemServiceClient() {

			// ========== 读操作：可以返回兜底数据（通常不在事务中） ==========

			@Override
			public ProductStringDTO getCode(Long skuId) {
				log.error("调用 item-service 获取商品详情失败, skuId: {}", skuId, cause);
				// 查询类接口返回兜底数据
				ProductStringDTO dto = new ProductStringDTO();
				dto.setSkuCode("商品信息获取失败");
				dto.setProductName("商品信息获取失败");
				return dto;
			}

			@Override
			public Map<String, Map<String, String>> batchGetProductInfo(List<SeckillProductSkuDTO> pIdToSId) {
				log.error("调用 item-service 批量获取商品信息失败, pIdToSId: {}", pIdToSId, cause);
				// 查询类接口返回 null
				return null;
			}

			@Override
			public SkuDetailVO getSkuDetail(Long skuId) {
				log.error("调用 item-service 获取SKU详情失败, skuId: {}", skuId, cause);
				// 查询类接口返回兜底数据
				return SkuDetailVO.builder()
						.skuId(skuId)
						.skuName("商品信息获取失败")
						.build();
			}

			@Override
			public List<SkuDetailVO> getSkuDetailBatch(List<Long> skuIds) {
				log.error("调用 item-service 批量获取SKU详情失败, skuIds: {}", skuIds, cause);
				// 查询类接口：返回空列表，由调用方降级处理（购物车会标记为"商品已失效"）
				return Collections.emptyList();
			}

			// ========== 写操作：必须抛出异常，让 Seata 触发回滚 ==========
			@Override
			public Boolean deductStock(Long skuId, Integer stock) {
				log.error("调用 item-service 扣减库存失败, skuId: {}, num: {}", skuId, stock, cause);
				// ✅ 写操作：抛出异常，触发分布式事务回滚
				throw new RuntimeException("调用商品服务扣减库存失败, skuId: " + skuId + ", stock: " + stock, cause);
			}

			@Override
			public List<BatchDeductStockResultVO> batchDeductStock(List<BatchDeductStockDTO> stockList) {
				log.error("调用 item-service 批量扣减库存失败, stockList: {}", stockList, cause);
				// ✅ 写操作：抛出异常，触发分布式事务回滚
				throw new RuntimeException("调用商品服务批量扣减库存失败", cause);
			}

			@Override
			public Boolean addStock(Long skuId, Integer stock) {
				log.error("调用 item-service 恢复库存失败, skuId: {}, stock: {}", skuId, stock, cause);
				// ✅ 写操作：抛出异常，触发分布式事务回滚
				throw new RuntimeException("调用商品服务恢复库存失败, skuId: " + skuId + ", stock: " + stock, cause);
			}

			@Override
			public Integer incrSoldCount(Long skuId, Integer num) {
				log.error("调用 item-service 累加销量失败, skuId: {}, num: {}", skuId, num, cause);
				// ✅ 写操作：抛出异常，触发分布式事务回滚
				throw new RuntimeException("调用商品服务累加销量失败, skuId: " + skuId + ", num: " + num, cause);
			}

		};
	}
}