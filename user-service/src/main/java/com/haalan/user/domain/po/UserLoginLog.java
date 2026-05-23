package com.haalan.user.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登录日志实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_user_login_log")
@ApiModel(value = "UserLoginLog对象", description = "用户登录日志表")
public class UserLoginLog implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "主键ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "用户名")
	@TableField("username")
	private String username;

	@ApiModelProperty(value = "登录IP地址")
	@TableField("login_ip")
	private String loginIp;

	@ApiModelProperty(value = "登录时间")
	@TableField("login_time")
	private LocalDateTime loginTime;

	@ApiModelProperty(value = "登录结果：0-失败 1-成功")
	@TableField("login_result")
	private Integer loginResult;

	@ApiModelProperty(value = "失败原因")
	@TableField("fail_reason")
	private String failReason;

	@ApiModelProperty(value = "浏览器信息")
	@TableField("browser")
	private String browser;

	@ApiModelProperty(value = "操作系统")
	@TableField("os")
	private String os;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;
}
