package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 退款列表项VO（管理端）
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@ApiModel("退款列表项")
public class RefundListItemVO {

	@ApiModelProperty("退款单号")
	private String refundNo;

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("用户ID")
	private Long userId;

	@ApiModelProperty("退款金额")
	private BigDecimal refundAmount;

	@ApiModelProperty("退款原因")
	private String refundReason;

	@ApiModelProperty("退款状态：0-处理中，1-退款成功，2-退款失败，3-已拒绝")
	private Integer status;

	@ApiModelProperty("退款状态名称")
	private String statusName;

	@ApiModelProperty("拒绝原因")
	private String rejectReason;

	@ApiModelProperty("申请时间")
	private LocalDateTime applyTime;

	@ApiModelProperty("处理时间")
	private LocalDateTime handleTime;

	@ApiModelProperty("完成时间")
	private LocalDateTime completeTime;
}
