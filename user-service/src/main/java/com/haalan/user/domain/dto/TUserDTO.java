package com.haalan.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class TUserDTO {
	@JsonProperty("username")
	private String username;  // 用户名    // 必填，3-20位字母数字组合
	@JsonProperty("password")
	private String password; // 必填，8-20位
	@JsonProperty("confirmPassword")
	private String confirmPassword; // 必填，与密码一致
	@JsonProperty("phone")
	private String phone;  // 必填，11位手机号
	@JsonProperty("email")
	private String email; // 可选，邮箱格式
	@JsonProperty("smsCode")
	private String smsCode;
	// 预留，短信验证码（后续升级）

}
