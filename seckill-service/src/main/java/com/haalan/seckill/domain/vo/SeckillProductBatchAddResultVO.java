package com.haalan.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillProductBatchAddResultVO", description = "批量添加秒杀商品响应")
public class SeckillProductBatchAddResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "成功数量")
	private Integer successCount;

	@ApiModelProperty(value = "失败列表")
	private List<FailedItem> failedList;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FailedItem {
		@ApiModelProperty(value = "SKU ID")
		private Long skuId;

		@ApiModelProperty(value = "失败原因")
		private String reason;
	}
}
