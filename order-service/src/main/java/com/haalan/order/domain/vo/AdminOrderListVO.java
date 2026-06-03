package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("管理端订单列表项")
public class AdminOrderListVO {

	@ApiModelProperty("订单ID")
	private Long id;

	@ApiModelProperty("订单号")
	private String orderNo;

	@ApiModelProperty("用户ID")
	private Long userId;

	@ApiModelProperty("订单金额")
	private BigDecimal totalAmount;

	@ApiModelProperty("实付金额")
	private BigDecimal actualAmount;

	@ApiModelProperty("订单类型：1-普通 2-秒杀")
	private Integer orderType;

	@ApiModelProperty("订单类型名称")
	private String orderTypeName;

	@ApiModelProperty("订单状态")
	private Integer status;

	@ApiModelProperty("状态名称")
	private String statusName;

	@ApiModelProperty("创建时间")
	private LocalDateTime createTime;
}
