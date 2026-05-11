package com.haalan.item.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ApiModel(value = "SkuStockUpdateDTO", description = "SKU库存更新请求")
public class SkuStockUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "库存数量", required = true)
	@NotNull(message = "库存数量不能为空")
	@Min(value = 0, message = "库存数量不能小于0")
	private Integer stock;

	@ApiModelProperty(value = "操作类型: ADD-增加, SET-设置, SUB-减少", required = true)
	@NotBlank(message = "操作类型不能为空")
	private String type;
}
