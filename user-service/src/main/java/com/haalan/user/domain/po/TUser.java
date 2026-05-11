package com.haalan.user.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_user")
@ApiModel(value = "TUser对象", description = "用户表")
public class TUser implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "用户ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "用户名")
	@TableField("username")
	private String username;

	@ApiModelProperty(value = "密码(加密)")
	@TableField("password")
	private String password;

	@ApiModelProperty(value = "手机号")
	@TableField("phone")
	private String phone;

	@ApiModelProperty(value = "邮箱")
	@TableField("email")
	private String email;

	@ApiModelProperty(value = "头像URL")
	@TableField("avatar")
	private String avatar;

	@ApiModelProperty(value = "状态: 0-禁用 1-正常")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "会员等级: 0-普通 1-白银 2-黄金 3-钻石")
	@TableField("member_level")
	private Integer memberLevel;

	@ApiModelProperty(value = "Token版本号")
	@TableField("token_version")
	private Integer tokenVersion;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;


}

