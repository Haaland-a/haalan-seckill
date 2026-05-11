package com.haalan.item.domain.dto;

import com.haalan.common.domain.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "SpuQueryDTO", description = "SPU查询参数")
public class SpuQueryDTO extends PageQuery {

	@ApiModelProperty(value = "分类ID")
	private Long categoryId;

	@ApiModelProperty(value = "关键词（商品名称/编码）")
	private String keyword;

	@ApiModelProperty(value = "状态: 0-下架 1-上架")
	private Integer status;
}
