package com.haalan.common.domain.po;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "商品索引库实体")
public class ProductDoc implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("SPU ID")
	private Long spuId;

	@ApiModelProperty("SPU编码")
	private String spuCode;

	@ApiModelProperty("商品名称")
	private String spuName;

	@ApiModelProperty("商品描述")
	private String description;

	@ApiModelProperty("主图URL")
	private String mainImage;

	@ApiModelProperty("商品图片列表JSON")
	private String images;

	@ApiModelProperty("分类ID")
	private Long categoryId;

	@ApiModelProperty("分类名称")
	private String categoryName;

	@ApiModelProperty("品牌ID")
	private Long brandId;

	@ApiModelProperty("品牌名称")
	private String brandName;

	@ApiModelProperty("SKU列表JSON")
	private String skuList;

	@ApiModelProperty("状态: 0-下架 1-上架")
	private Integer status;

	@Data
	@ApiModel(description = "SKU信息")
	public static class SkuDoc implements Serializable {

		private static final long serialVersionUID = 1L;

		@ApiModelProperty("SKU ID")
		private Long skuId;

		@ApiModelProperty("SKU编码")
		private String skuCode;

		@ApiModelProperty("SKU名称")
		private String skuName;

		@ApiModelProperty("价格（分）")
		private Integer price;

		@ApiModelProperty("原价（分）")
		private Integer originalPrice;

		@ApiModelProperty("规格参数JSON")
		private String specifications;

		@ApiModelProperty("SKU图片列表JSON")
		private String images;

		@ApiModelProperty("状态: 0-下架 1-上架")
		private Integer status;
	}
}
