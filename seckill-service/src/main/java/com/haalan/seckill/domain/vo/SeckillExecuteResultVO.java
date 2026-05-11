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
@ApiModel(value = "SeckillExecuteResultVO", description = "秒杀执行结果")
public class SeckillExecuteResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "是否成功（true-同步成功，false-排队中）")
	private Boolean success;

	@ApiModelProperty(value = "订单信息（成功时返回）")
	private SeckillOrderVO orderVO;

	@ApiModelProperty(value = "排队信息（排队时返回）")
	private SeckillQueueVO queueVO;
}
