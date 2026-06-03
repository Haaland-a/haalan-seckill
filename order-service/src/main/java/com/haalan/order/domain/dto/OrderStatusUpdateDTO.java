package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("订单状态更新请求")
public class OrderStatusUpdateDTO {

	@NotNull(message = "状态不能为空")
	@Min(value = 0, message = "状态值无效")
	@Max(value = 4, message = "状态值无效")
	@ApiModelProperty(value = "目标状态：1-已支付 2-已发货 3-已完成 4-已取消", required = true)
	private Integer status;

	@ApiModelProperty("备注/原因")
	private String remark;
}
