package com.haalan.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("管理端用户查询")
public class AdminUserQueryDTO {

	@ApiModelProperty("用户名（模糊）")
	private String username;

	@ApiModelProperty("手机号（模糊）")
	private String phone;

	@ApiModelProperty("状态：0-禁用 1-正常")
	private Integer status;

	@ApiModelProperty("会员等级：0-普通 1-白银 2-黄金 3-钻石")
	private Integer memberLevel;

	@ApiModelProperty("页码")
	private Integer pageNum = 1;

	@ApiModelProperty("每页条数")
	private Integer pageSize = 10;
}
