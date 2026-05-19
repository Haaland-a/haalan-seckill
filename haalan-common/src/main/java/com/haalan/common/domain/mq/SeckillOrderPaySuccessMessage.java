package com.haalan.common.domain.mq;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀订单支付成功消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeckillOrderPaySuccessMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "用户ID")
	private Long userId;


}
