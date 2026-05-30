package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("订单商品项")
public class OrderItemDTO {

	@NotNull(message = "商品SKU ID不能为空")
	@ApiModelProperty(value = "SKU ID", required = true)
	private Long skuId;

	@NotNull(message = "购买数量不能为空")
	@Min(value = 1, message = "购买数量不能小于1")
	@ApiModelProperty(value = "购买数量", required = true, example = "1")
	private Integer quantity;
}
