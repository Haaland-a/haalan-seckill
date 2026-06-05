package com.haalan.item.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "SpuUpdateDTO", description = "修改SPU请求")
public class SpuUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "商品名称", required = true)
	@NotBlank(message = "商品名称不能为空")
	private String name;

	@ApiModelProperty(value = "分类ID", required = true)
	@NotNull(message = "分类ID不能为空")
	private Long categoryId;

	@ApiModelProperty(value = "品牌ID")
	private Long brandId;

	@ApiModelProperty(value = "商品描述")
	private String description;

	@ApiModelProperty(value = "主图URL")
	private String mainImage;

	@ApiModelProperty(value = "商品图片列表")
	private List<String> images;

	@ApiModelProperty(value = "状态: 0-下架 1-上架")
	private Integer status;
}
