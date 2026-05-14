package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 取消订单响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "CancelOrderResponseVO", description = "取消订单响应")
public class CancelOrderResponseVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "订单状态")
	private Integer status;

	@ApiModelProperty(value = "订单状态名称")
	private String statusName;
}
