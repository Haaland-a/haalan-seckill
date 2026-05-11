package com.haalan.seckill.domain.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillTokenVO", description = "秒杀令牌响应")
public class SeckillTokenVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "秒杀令牌")
	private String seckillToken;

	@ApiModelProperty(value = "有效期(秒)")
	private Integer expireTime;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;
}
