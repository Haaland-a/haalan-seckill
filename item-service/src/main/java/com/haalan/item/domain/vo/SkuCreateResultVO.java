package com.haalan.item.domain.vo;

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
@ApiModel(value = "SkuCreateResultVO", description = "创建SKU响应")
public class SkuCreateResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU ID")
	private Long skuId;
}
