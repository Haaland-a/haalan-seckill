package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * <p>
 * 退款申请请求DTO
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@ApiModel("退款申请请求")
public class RefundRequestDTO {

	@NotBlank(message = "订单号不能为空")
	@ApiModelProperty(value = "订单号", required = true)
	private String orderNo;

	@NotNull(message = "退款金额不能为空")
	@DecimalMin(value = "0.01", message = "退款金额必须大于0")
	@ApiModelProperty(value = "退款金额", required = true)
	private BigDecimal refundAmount;

	@ApiModelProperty("退款原因")
	private String refundReason;
}
