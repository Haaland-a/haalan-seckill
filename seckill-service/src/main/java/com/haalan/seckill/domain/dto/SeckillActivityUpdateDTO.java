package com.haalan.seckill.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Data
@ApiModel(value = "SeckillActivityUpdateDTO", description = "修改秒杀活动请求")
public class SeckillActivityUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动名称")
	private String activityName;

	@ApiModelProperty(value = "结束时间", example = "2026-01-01 14:00:00")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "状态: 0-未开始 1-进行中 2-已结束")
	@Min(value = 0, message = "状态值非法")
	@Max(value = 2, message = "状态值非法")
	private Integer status;
}
