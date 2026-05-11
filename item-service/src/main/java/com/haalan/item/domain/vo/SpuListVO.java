package com.haalan.item.domain.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@ApiModel(value = "SpuListVO", description = "SPU列表项")
public class SpuListVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SPU ID")
	private Long spuId;

	@ApiModelProperty(value = "SPU编码")
	private String spuCode;

	@ApiModelProperty(value = "商品名称")
	private String name;

	@ApiModelProperty(value = "分类ID")
	private Long categoryId;

	@ApiModelProperty(value = "分类名称")
	private String categoryName;

	@ApiModelProperty(value = "品牌名称")
	private String brandName;

	@ApiModelProperty(value = "主图URL")
	private String mainImage;

	@ApiModelProperty(value = "状态: 0-下架 1-上架")
	private Integer status;

	@ApiModelProperty(value = "状态名称")
	private String statusName;

	@ApiModelProperty(value = "创建时间")
	private LocalDateTime createTime;
}
