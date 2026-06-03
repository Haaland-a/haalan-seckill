package com.haalan.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("登录日志")
public class LoginLogVO {

	@ApiModelProperty("日志ID")
	private Long id;

	@ApiModelProperty("用户ID")
	private Long userId;

	@ApiModelProperty("用户名")
	private String username;

	@ApiModelProperty("登录IP")
	private String loginIp;

	@ApiModelProperty("登录时间")
	private LocalDateTime loginTime;

	@ApiModelProperty("登录结果：0-失败 1-成功")
	private Integer loginResult;

	@ApiModelProperty("登录结果名称")
	private String loginResultName;

	@ApiModelProperty("失败原因")
	private String failReason;

	@ApiModelProperty("浏览器")
	private String browser;

	@ApiModelProperty("操作系统")
	private String os;
}
