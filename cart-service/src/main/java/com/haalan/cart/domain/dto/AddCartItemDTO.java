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
 * 加入购物车请求
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@ApiModel(value = "AddCartItemDTO", description = "加入购物车请求")
public class AddCartItemDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU ID", required = true)
	@NotNull(message = "skuId不能为空")
	private Long skuId;

	@ApiModelProperty(value = "数量", required = true)
	@NotNull(message = "数量不能为空")
	@Min(value = 1, message = "数量必须大于0")
	@Max(value = 200, message = "单个商品数量不能超过200")
	private Integer quantity;
}
