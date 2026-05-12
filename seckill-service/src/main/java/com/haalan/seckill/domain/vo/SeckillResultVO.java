package com.haalan.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillResultVO", description = "秒杀结果查询响应")
public class SeckillResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "请求ID")
	private String requestId;

	@ApiModelProperty(value = "状态: PROCESSING-处理中, SUCCESS-成功, FAILED-失败")
	private String status;

	@ApiModelProperty(value = "订单号（成功时返回）")
	private String orderNo;

	@ApiModelProperty(value = "订单信息（成功时返回）")
	private SeckillOrderVO orderInfo;

	@ApiModelProperty(value = "失败原因（失败时返回）")
	private String failReason;
}