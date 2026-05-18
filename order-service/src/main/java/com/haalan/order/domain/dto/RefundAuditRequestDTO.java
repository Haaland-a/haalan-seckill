package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * 退款审核请求DTO（管理端）
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@ApiModel("退款审核请求")
public class RefundAuditRequestDTO {

	@NotBlank(message = "退款单号不能为空")
	@ApiModelProperty(value = "退款单号", required = true)
	private String refundNo;

	@NotNull(message = "审核结果不能为空")
	@ApiModelProperty(value = "是否通过：true-通过，false-拒绝", required = true)
	private Boolean approved;

	@ApiModelProperty("拒绝原因（拒绝时必填）")
	private String rejectReason;
}
