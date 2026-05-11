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
@ApiModel(value = "SeckillQueueVO", description = "秒杀排队响应")
public class SeckillQueueVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "请求ID")
	private String requestId;

	@ApiModelProperty(value = "订单号（处理完成后填充）")
	private String orderNo;

	@ApiModelProperty(value = "队列位置")
	private Integer queuePosition;

	@ApiModelProperty(value = "预计等待时间（秒）")
	private Integer estimatedTime;
}
