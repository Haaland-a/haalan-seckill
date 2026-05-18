package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * <p>
 * 退款申请响应VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("退款申请响应")
public class RefundResponseVO {

	@ApiModelProperty("退款单号")
	private String refundNo;

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("退款金额")
	private BigDecimal refundAmount;

	@ApiModelProperty("退款状态：0-处理中，1-退款成功，2-退款失败，3-已拒绝")
	private Integer status;

	@ApiModelProperty("退款状态名称")
	private String statusName;
}
