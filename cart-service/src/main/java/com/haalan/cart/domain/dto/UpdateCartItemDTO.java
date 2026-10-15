package com.haalan.cart.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 * 修改购物车数量请求
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@ApiModel(value = "UpdateCartItemDTO", description = "修改购物车数量请求")
public class UpdateCartItemDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "数量", required = true)
	@NotNull(message = "数量不能为空")
	@Min(value = 1, message = "数量必须大于0")
	@Max(value = 200, message = "单个商品数量不能超过200")
	private Integer quantity;
}
