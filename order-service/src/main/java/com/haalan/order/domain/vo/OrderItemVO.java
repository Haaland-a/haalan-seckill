package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * <p>
 * 订单商品明细VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@ApiModel("订单商品明细")
public class OrderItemVO {

	@ApiModelProperty("SKU ID")
	private Long skuId;

	@ApiModelProperty("SPU ID")
	private Long spuId;

	@ApiModelProperty("商品名称")
	private String productName;

	@ApiModelProperty("商品图片")
	private String productImage;

	@ApiModelProperty("规格信息")
	private Map<String, String> specifications;

	@ApiModelProperty("商品价格")
	private BigDecimal price;

	@ApiModelProperty("购买数量")
	private Integer quantity;

	@ApiModelProperty("商品总价")
	private BigDecimal totalPrice;
}
