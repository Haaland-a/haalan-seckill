package com.haalan.seckill.domain.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillProductBatchAddDTO", description = "批量添加秒杀商品请求")
public class SeckillProductBatchAddDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Valid
	@NotEmpty(message = "商品列表不能为空")
	@ApiModelProperty(value = "商品列表", required = true)
	private List<SeckillActivityAddPDTO> products;
}
