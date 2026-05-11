package com.haalan.item.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "CategoryVO", description = "商品分类VO")
public class CategoryVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "分类ID")
	private Long categoryId;

	@ApiModelProperty(value = "分类名称")
	private String name;

	@ApiModelProperty(value = "分类层级")
	private Integer level;

	@ApiModelProperty(value = "子分类列表")
	private List<CategoryVO> children;  // 子分类列表
}
