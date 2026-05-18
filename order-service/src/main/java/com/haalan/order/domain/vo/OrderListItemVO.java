package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单列表项VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@ApiModel("订单列表项")
public class OrderListItemVO {

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("订单总金额")
	private BigDecimal totalAmount;

	@ApiModelProperty("优惠金额")
	private BigDecimal discountAmount;

	@ApiModelProperty("实付金额")
	private BigDecimal actualAmount;

	@ApiModelProperty("订单类型: 1-普通 2-秒杀 3-团购")
	private Integer orderType;

	@ApiModelProperty("订单类型名称")
	private String orderTypeName;

	@ApiModelProperty("订单状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消")
	private Integer status;

	@ApiModelProperty("订单状态名称")
	private String statusName;


	@ApiModelProperty("商品图片")
	private String productImage;

	@ApiModelProperty("商品名称")
	private String productName;

	@ApiModelProperty("商品数量")
	private Integer quantity;

	@ApiModelProperty("创建时间")
	private LocalDateTime createTime;

	@ApiModelProperty("更新时间")
	private LocalDateTime updateTime;
}
