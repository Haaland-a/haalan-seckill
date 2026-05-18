package com.haalan.seckill.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillExecuteRequestDTO", description = "秒杀执行请求")
public class SeckillExecuteRequestDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "客户端生成的唯一请求ID，用于幂等", required = true)
	@NotBlank(message = "请求ID不能为空")
	private String requestId;

	@ApiModelProperty(value = "活动ID", required = true)
	@NotNull(message = "活动ID不能为空")
	private Long activityId;

	@ApiModelProperty(value = "秒杀商品ID", required = true)
	@NotNull(message = "秒杀商品ID不能为空")
	private Long seckillProductId;

	@ApiModelProperty(value = "SKU ID", required = true)
	@NotNull(message = "SKU ID不能为空")
	private Long skuId;

	@ApiModelProperty(value = "秒杀令牌", required = true)
	@NotBlank(message = "秒杀令牌不能为空")
	private String seckillToken;

	@ApiModelProperty(value = "购买数量", required = true)
	@NotNull(message = "购买数量不能为空")
	@Min(value = 1, message = "购买数量必须大于0")
	private Integer quantity;

	@ApiModelProperty(value = "收货地址ID（加密后）", required = true)
	@NotBlank(message = "收货地址ID不能为空")
	private String addressId;
}
