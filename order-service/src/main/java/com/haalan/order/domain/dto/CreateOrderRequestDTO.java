package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ApiModel("创建订单请求")
public class CreateOrderRequestDTO {

	@NotNull(message = "收货地址不能为空")
	@ApiModelProperty(value = "收货地址ID", required = true)
	private Long addressId;

	@ApiModelProperty(value = "备注")
	private String remark;

	@NotEmpty(message = "商品列表不能为空")
	@Valid
	@ApiModelProperty(value = "商品列表", required = true)
	private List<OrderItemDTO> items;
}
