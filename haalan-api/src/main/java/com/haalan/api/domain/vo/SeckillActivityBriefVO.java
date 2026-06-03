package com.haalan.api.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeckillActivityBriefVO {
	private Long activityId;
	private String activityName;
	private Integer status;
	private LocalDateTime startTime;
	private LocalDateTime endTime;
	private Integer totalStock;
}
