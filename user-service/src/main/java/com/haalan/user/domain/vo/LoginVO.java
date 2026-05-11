package com.haalan.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "登录响应数据")
@NoArgsConstructor
@Data
public class LoginVO {
	@ApiModelProperty(value = "用户ID", example = "1001")
	@JsonProperty("userId")
	private Long userId;

	@ApiModelProperty(value = "用户名", example = "admin")
	@JsonProperty("username")
	private String username;

	@ApiModelProperty(value = "JWT令牌")
	@JsonProperty("token")
	private String token;

	@ApiModelProperty(value = "Token过期时间戳", example = "1712995200000")
	@JsonProperty("tokenExpire")
	private Long tokenExpire;

	@ApiModelProperty(value = "会员等级：1-普通 2-VIP 3-SVIP", example = "1")
	@JsonProperty("memberLevel")
	private Integer memberLevel;
}
