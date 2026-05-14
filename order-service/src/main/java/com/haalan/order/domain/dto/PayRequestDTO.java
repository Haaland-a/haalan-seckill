package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 支付请求DTO
 */
@Data
@ApiModel(value = "PayRequestDTO", description = "支付请求")
public class PayRequestDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号", required = true)
	@NotBlank(message = "订单号不能为空")
	private String orderNo;

	@ApiModelProperty(value = "支付方式：1-微信支付 2-支付宝 3-银行卡", required = true)
	@NotNull(message = "支付方式不能为空")
	private Integer payType;

	@ApiModelProperty(value = "支付成功回调地址")
	private String returnUrl;
}
