package com.haalan.seckill.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillProductUpdateDTO", description = "秒杀商品信息更新请求")
public class SeckillProductUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "秒杀价", required = true)
	@NotNull(message = "秒杀价不能为空")
	@DecimalMin(value = "0.01", message = "秒杀价必须大于0")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "每人限购数量", required = true)
	@NotNull(message = "限购数量不能为空")
	@Min(value = 1, message = "限购数量不能小于1")
	private Integer limitPerUser;
}
