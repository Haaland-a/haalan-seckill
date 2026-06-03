package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@ApiModel("活动销售统计")
public class ActivityStatsVO {

	@ApiModelProperty("活动ID")
	private Long activityId;

	@ApiModelProperty("活动名称")
	private String activityName;

	@ApiModelProperty("活动状态：0-未开始 1-进行中 2-已结束")
	private Integer status;

	@ApiModelProperty("活动状态名称")
	private String statusName;

	@ApiModelProperty("开始时间")
	private LocalDateTime startTime;

	@ApiModelProperty("结束时间")
	private LocalDateTime endTime;

	@ApiModelProperty("订单数")
	private Long orderCount;

	@ApiModelProperty("总销售额")
	private BigDecimal totalAmount;

	@ApiModelProperty("已售库存")
	private Integer soldStock;

	@ApiModelProperty("总库存")
	private Integer totalStock;

	@ApiModelProperty("售罄率")
	private BigDecimal sellOutRate;

	@ApiModelProperty("平均单价")
	private BigDecimal avgPrice;

	public BigDecimal getSellOutRate() {
		if (totalStock != null && totalStock > 0 && soldStock != null) {
			return BigDecimal.valueOf(soldStock)
					.divide(BigDecimal.valueOf(totalStock), 4, RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100))
					.setScale(2, RoundingMode.HALF_UP);
		}
		return BigDecimal.ZERO;
	}

	public BigDecimal getAvgPrice() {
		if (orderCount != null && orderCount > 0 && totalAmount != null) {
			return totalAmount.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
		}
		return BigDecimal.ZERO;
	}
}
