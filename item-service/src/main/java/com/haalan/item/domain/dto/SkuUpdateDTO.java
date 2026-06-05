package com.haalan.item.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(value = "SkuUpdateDTO", description = "修改SKU请求")
public class SkuUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU名称", required = true)
	@NotBlank(message = "SKU名称不能为空")
	private String name;

	@ApiModelProperty(value = "销售价格", required = true)
	@NotNull(message = "销售价格不能为空")
	@DecimalMin(value = "0.01", message = "价格必须大于0")
	private BigDecimal price;

	@ApiModelProperty(value = "促销价格")
	private BigDecimal promotionPrice;

	@ApiModelProperty(value = "规格参数")
	private Map<String, String> specifications;

	@ApiModelProperty(value = "SKU图片列表")
	private List<String> images;
}
