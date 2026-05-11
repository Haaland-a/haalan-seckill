package com.haalan.order.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单商品明细表
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_order_item")
@ApiModel(value = "TOrderItem对象", description = "订单商品明细表")
public class TOrderItem implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "明细ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "订单ID")
	@TableField("order_id")
	private Long orderId;

	@ApiModelProperty(value = "订单号")
	@TableField("order_no")
	private String orderNo;

	@ApiModelProperty(value = "SKU ID")
	@TableField("sku_id")
	private Long skuId;

	@ApiModelProperty(value = "SKU编码")
	@TableField("sku_code")
	private String skuCode;

	@ApiModelProperty(value = "商品名称")
	@TableField("product_name")
	private String productName;

	@ApiModelProperty(value = "商品图片")
	@TableField("product_image")
	private String productImage;

	@ApiModelProperty(value = "单价")
	@TableField("price")
	private BigDecimal price;

	@ApiModelProperty(value = "数量")
	@TableField("quantity")
	private Integer quantity;

	@ApiModelProperty(value = "总价")
	@TableField("total_price")
	private BigDecimal totalPrice;

	@ApiModelProperty(value = "规格信息")
	@TableField("specifications")
	private String specifications;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;


}
