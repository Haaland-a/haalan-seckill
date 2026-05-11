package com.haalan.seckill.domain.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillTokenRequestDTO", description = "秒杀令牌生成请求")
public class SeckillTokenRequestDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动ID", required = true)
	@NotNull(message = "活动ID不能为空")
	private Long activityId;

	@ApiModelProperty(value = "秒杀商品ID", required = true)
	@NotNull(message = "秒杀商品ID不能为空")
	private Long seckillProductId;

	@ApiModelProperty(value = "客户端信息(防刷用)")
	private ClientInfo clientInfo;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@ApiModel(value = "ClientInfo", description = "客户端信息")
	public static class ClientInfo implements Serializable {

		private static final long serialVersionUID = 1L;

		@ApiModelProperty(value = "设备ID")
		private String deviceId;

		@ApiModelProperty(value = "用户代理")
		private String userAgent;
	}
}
