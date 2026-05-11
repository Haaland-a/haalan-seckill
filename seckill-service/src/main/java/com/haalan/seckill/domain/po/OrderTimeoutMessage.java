package com.haalan.seckill.domain.po;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;           // 订单号
	@ApiModelProperty(value = "活动ID")
	private Long activityId;          // 活动ID
	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;    // 秒杀商品ID
	@ApiModelProperty(value = "SKU ID")
	private Long skuId;               // SKU ID
	@ApiModelProperty(value = "用户ID")
	private Long userId;              // 用户ID
	@ApiModelProperty(value = "购买数量")
	private Integer quantity;         // 购买数量
}