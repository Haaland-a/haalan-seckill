package com.haalan.seckill.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillOrderVO", description = "秒杀订单响应")
public class SeckillOrderVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;

	@ApiModelProperty(value = "商品名称")
	private String productName;

	@ApiModelProperty(value = "秒杀单价")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "购买数量")
	private Integer quantity;

	@ApiModelProperty(value = "订单总金额")
	private BigDecimal totalAmount;

	@ApiModelProperty(value = "支付截止时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime payExpireTime;

}
