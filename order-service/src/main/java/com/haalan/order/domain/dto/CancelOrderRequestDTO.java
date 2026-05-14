package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 取消订单请求DTO
 */
@Data
@ApiModel(value = "CancelOrderRequestDTO", description = "取消订单请求")
public class CancelOrderRequestDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "取消原因", required = true)
	@NotBlank(message = "取消原因不能为空")
	private String cancelReason;
}
