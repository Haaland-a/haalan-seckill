package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("平台总览统计")
public class OverviewStatsVO {

	@ApiModelProperty("总用户数")
	private Long totalUsers;

	@ApiModelProperty("总订单数")
	private Long totalOrders;

	@ApiModelProperty("总销售额（已支付/已发货/已完成）")
	private BigDecimal totalRevenue;

	@ApiModelProperty("待处理退款数")
	private Long pendingRefunds;

	@ApiModelProperty("今日订单数")
	private Long todayOrders;

	@ApiModelProperty("今日销售额")
	private BigDecimal todayRevenue;
}
