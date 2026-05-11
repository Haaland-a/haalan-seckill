package com.haalan.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "登录请求参数")
@NoArgsConstructor
@Data
public class LoginDTO {
	@ApiModelProperty(value = "用户名", example = "admin")
	@JsonProperty("username")
	private String username;

	@ApiModelProperty(value = "密码", example = "123456")
	@JsonProperty("password")
	private String password;
}
