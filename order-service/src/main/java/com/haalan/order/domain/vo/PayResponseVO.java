package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "PayResponseVO", description = "支付响应")
public class PayResponseVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "支付金额")
	private BigDecimal payAmount;

	@ApiModelProperty(value = "支付方式：1-微信支付 2-支付宝 3-银行卡")
	private Integer payType;

	@ApiModelProperty(value = "支付方式名称")
	private String payTypeName;

	@ApiModelProperty(value = "支付链接/二维码URL")
	private String payUrl;

	@ApiModelProperty(value = "二维码Base64")
	private String qrCode;

	@ApiModelProperty(value = "支付过期时间")
	private LocalDateTime expireTime;
}
