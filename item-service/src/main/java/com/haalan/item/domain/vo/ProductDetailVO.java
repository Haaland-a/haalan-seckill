package com.haalan.item.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(description = "商品详情VO")
public class ProductDetailVO {

	@ApiModelProperty("SPU ID")
	private Long spuId;

	@ApiModelProperty("SPU编码")
	private String spuCode;

	@ApiModelProperty("SPU名称")
	private String spuName;

	@ApiModelProperty("商品描述")
	private String description;

	@ApiModelProperty("主图URL")
	private String mainImage;

	@ApiModelProperty("商品图片列表")
	private List<String> images;

	@ApiModelProperty("分类ID")
	private Long categoryId;

	@ApiModelProperty("分类名称")
	private String categoryName;

	@ApiModelProperty("品牌ID")
	private Long brandId;

	@ApiModelProperty("品牌名称")
	private String brandName;

	@ApiModelProperty("SKU列表")
	private List<SkuDetailVO> skuList;

	@Data
	@ApiModel(description = "SKU详情VO")
	public static class SkuDetailVO {

		@ApiModelProperty("SKU ID")
		private Long skuId;

		@ApiModelProperty("SKU编码")
		private String skuCode;

		@ApiModelProperty("SKU名称")
		private String skuName;

		@ApiModelProperty("销售价格")
		private BigDecimal price;

		@ApiModelProperty("原价")
		private BigDecimal originalPrice;

		@ApiModelProperty("库存数量")
		private Long stock;

		@ApiModelProperty("销量")
		private Long soldCount;

		@ApiModelProperty("状态")
		private Integer status;

		@ApiModelProperty("规格参数")
		private Map<String, Object> specifications;

		@ApiModelProperty("SKU图片列表")
		private List<String> images;
	}
}
