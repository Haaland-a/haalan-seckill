package com.haalan.cart.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 购物车汇总 VO
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@ApiModel(value = "CartVO", description = "购物车")
public class CartVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("购物车项列表")
	private List<CartItemVO> items;

	@ApiModelProperty("购物车商品种类数（有效商品）")
	private Integer totalCount;

	@ApiModelProperty("购物车商品总件数（有效商品）")
	private Integer totalQuantity;

	@ApiModelProperty("已勾选商品总金额")
	private BigDecimal selectedAmount;
}
