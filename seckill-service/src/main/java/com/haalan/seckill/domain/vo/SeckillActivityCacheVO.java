package com.haalan.seckill.domain.vo;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillActivityCacheVO", description = "Redis中的秒杀活动信息")
public class SeckillActivityCacheVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	@ApiModelProperty(value = "活动名称")
	private String activityName;

	@ApiModelProperty(value = "开始时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime startTime;

	@ApiModelProperty(value = "结束时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "状态: 0-未开始 1-进行中 2-已结束")
	private Integer status;

	@ApiModelProperty(value = "状态名称")
	private String statusName;

	@ApiModelProperty(value = "活动描述")
	private String activityDesc;

	@ApiModelProperty(value = "商品数量")
	private Integer productCount;
}
