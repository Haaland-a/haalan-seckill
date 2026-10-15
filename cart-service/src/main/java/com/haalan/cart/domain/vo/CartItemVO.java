package com.haalan.cart.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * <p>
 * 购物车单项 VO（含实时商品信息）
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@ApiModel(value = "CartItemVO", description = "购物车单项")
public class CartItemVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("购物车项ID")
	private Long id;

	@ApiModelProperty("SKU ID")
	private Long skuId;

	@ApiModelProperty("SPU ID")
	private Long spuId;

	@ApiModelProperty("商品名称")
	private String productName;

	@ApiModelProperty("商品图片")
	private String productImage;

	@ApiModelProperty("规格参数")
	private Map<String, Object> specifications;

	@ApiModelProperty("当前单价")
	private BigDecimal price;

	@ApiModelProperty("数量")
	private Integer quantity;

	@ApiModelProperty("小计金额")
	private BigDecimal totalPrice;

	@ApiModelProperty("当前库存")
	private Integer stock;

	@ApiModelProperty("是否勾选：0-未选，1-已选")
	private Integer selected;

	@ApiModelProperty("是否有效：false 表示商品已下架或已删除")
	private Boolean valid;

	@ApiModelProperty("失效原因")
	private String invalidReason;
}
