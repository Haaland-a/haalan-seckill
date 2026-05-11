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
@ApiModel(value = "SpuCreateResultVO", description = "创建SPU响应")
public class SpuCreateResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SPU ID")
	private Long spuId;
}
