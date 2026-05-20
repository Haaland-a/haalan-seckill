package com.haalan.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "修改密码请求参数")
@NoArgsConstructor
@Data
public class ChangePasswordDTO {

	@ApiModelProperty(value = "原密码", example = "123456", required = true)
	@JsonProperty("oldPassword")
	private String oldPassword;

	@ApiModelProperty(value = "新密码", example = "newpassword123", required = true)
	@JsonProperty("newPassword")
	private String newPassword;

	@ApiModelProperty(value = "确认新密码", example = "newpassword123", required = true)
	@JsonProperty("confirmNewPassword")
	private String confirmNewPassword;
}