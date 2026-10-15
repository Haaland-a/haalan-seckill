package com.haalan.cart.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 * 勾选/取消勾选购物车项请求
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@ApiModel(value = "SelectCartItemDTO", description = "勾选购物车项请求")
public class SelectCartItemDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "是否勾选：0-未选，1-已选", required = true)
	@NotNull(message = "selected不能为空")
	private Integer selected;
}
