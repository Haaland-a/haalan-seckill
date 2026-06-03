package com.haalan.order.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ActivityStatsRaw {
	private Long activityId;
	private Long orderCount;
	private BigDecimal totalAmount;
	private Integer soldStock;
}
