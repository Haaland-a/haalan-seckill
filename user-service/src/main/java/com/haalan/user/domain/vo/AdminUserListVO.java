package com.haalan.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("管理端用户列表项")
public class AdminUserListVO {

	@ApiModelProperty("用户ID")
	private Long userId;

	@ApiModelProperty("用户名")
	private String username;

	@ApiModelProperty("手机号")
	private String phone;

	@ApiModelProperty("邮箱")
	private String email;

	@ApiModelProperty("状态：0-禁用 1-正常")
	private Integer status;

	@ApiModelProperty("状态名称")
	private String statusName;

	@ApiModelProperty("会员等级：0-普通 1-白银 2-黄金 3-钻石")
	private Integer memberLevel;

	@ApiModelProperty("会员等级名称")
	private String memberLevelName;

	@ApiModelProperty("注册时间")
	private LocalDateTime createTime;
}
