package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单详情VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@ApiModel("订单详情")
public class OrderDetailVO {

	@ApiModelProperty("订单ID")
	private Long orderId;

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("用户ID")
	private Long userId;

	@ApiModelProperty("订单类型: 1-普通 2-秒杀 3-团购")
	private Integer orderType;

	@ApiModelProperty("订单类型名称")
	private String orderTypeName;

	@ApiModelProperty("订单总金额")
	private BigDecimal totalAmount;

	@ApiModelProperty("优惠金额")
	private BigDecimal discountAmount;

	@ApiModelProperty("实付金额")
	private BigDecimal actualAmount;

	@ApiModelProperty("订单状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消")
	private Integer status;

	@ApiModelProperty("订单状态名称")
	private String statusName;

	@ApiModelProperty("创建时间")
	private LocalDateTime createTime;

	@ApiModelProperty("支付过期时间")
	private LocalDateTime payExpireTime;

	@ApiModelProperty("剩余支付时间（秒）")
	private Long remainingSeconds;

	@ApiModelProperty("订单商品列表")
	private List<OrderItemVO> orderItems;

	@ApiModelProperty("收货地址信息")
	private AddressInfoVO addressInfo;
}
