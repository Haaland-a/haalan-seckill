package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 支付结果查询响应VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("支付结果查询响应")
public class PayResultVO {

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("支付状态: 0-待支付 1-已支付 2-支付失败")
	private Integer payStatus;

	@ApiModelProperty("支付状态名称")
	private String payStatusName;

	@ApiModelProperty("支付金额")
	private BigDecimal payAmount;

	@ApiModelProperty("支付方式: 1-微信 2-支付宝")
	private Integer payType;

	@ApiModelProperty("支付方式名称")
	private String payTypeName;

	@ApiModelProperty("第三方支付交易号")
	private String transactionId;

	@ApiModelProperty("支付时间")
	private LocalDateTime payTime;
}
