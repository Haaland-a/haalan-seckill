package com.haalan.common.domain.mq;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>
 * 秒杀订单退款成功MQ消息
 * </p>
 *
 * @author haaland
 * @since 2026-05-22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeckillOrderRefundSuccessMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "用户ID")
	private Long userId;

	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;

	@ApiModelProperty(value = "购买数量")
	private Integer quantity;

}
