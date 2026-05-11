package com.haalan.seckill.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillProductStockUpdateDTO", description = "秒杀商品库存更新请求")
public class SeckillProductStockUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "库存数量", required = true)
	@NotNull(message = "库存数量不能为空")
	@Min(value = 0, message = "库存数量不能小于0")
	private Integer stock;

	@ApiModelProperty(value = "是否同步到Redis", example = "true")
	private Boolean syncToRedis = false;
}
