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
@ApiModel(value = "SeckillProductStockUpdateResultVO", description = "秒杀商品库存更新响应")
public class SeckillProductStockUpdateResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;

	@ApiModelProperty(value = "当前库存")
	private Integer currentStock;

	@ApiModelProperty(value = "是否已同步到Redis")
	private Boolean redisSynced;
}
