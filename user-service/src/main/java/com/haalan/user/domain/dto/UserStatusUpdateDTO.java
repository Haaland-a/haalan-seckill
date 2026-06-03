package com.haalan.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("用户状态更新请求")
public class UserStatusUpdateDTO {

	@NotNull(message = "状态不能为空")
	@Min(value = 0, message = "状态值无效：0-禁用 1-正常")
	@Max(value = 1, message = "状态值无效：0-禁用 1-正常")
	@ApiModelProperty(value = "状态：0-禁用 1-正常", required = true)
	private Integer status;
}
