package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("管理端订单查询")
public class AdminOrderQueryDTO {

	@ApiModelProperty("订单号（模糊）")
	private String orderNo;

	@ApiModelProperty("订单状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消")
	private Integer status;

	@ApiModelProperty("订单类型：1-普通 2-秒杀")
	private Integer orderType;

	@ApiModelProperty("开始日期")
	private String startDate;

	@ApiModelProperty("结束日期")
	private String endDate;

	@ApiModelProperty("页码")
	private Integer pageNum = 1;

	@ApiModelProperty("每页条数")
	private Integer pageSize = 10;
}
