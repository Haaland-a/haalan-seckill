package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("每日销售趋势")
public class DailyTrendVO {

	@ApiModelProperty("日期")
	private String date;

	@ApiModelProperty("订单数")
	private Long orderCount;

	@ApiModelProperty("销售额")
	private BigDecimal amount;
}
