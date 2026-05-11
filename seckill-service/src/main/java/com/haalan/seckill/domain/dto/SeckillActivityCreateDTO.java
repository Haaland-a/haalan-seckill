package com.haalan.seckill.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Data
@ApiModel(value = "SeckillActivityCreateDTO", description = "创建秒杀活动请求")
public class SeckillActivityCreateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动名称", required = true)
	@NotBlank(message = "活动名称不能为空")
	private String activityName;

	@ApiModelProperty(value = "开始时间", required = true, example = "2026-01-01 10:00:00")
	@NotNull(message = "开始时间不能为空")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime startTime;

	@ApiModelProperty(value = "结束时间", required = true, example = "2026-01-01 12:00:00")
	@NotNull(message = "结束时间不能为空")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "活动描述")
	private String activityDesc;

	@ApiModelProperty(value = "每人限购数量", required = true)
	@NotNull(message = "每人限购数量不能为空")
	@Positive(message = "每人限购数量必须大于0")
	private Integer limitPerUser;
}
