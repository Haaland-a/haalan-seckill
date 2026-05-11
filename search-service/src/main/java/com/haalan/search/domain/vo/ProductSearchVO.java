package com.haalan.search.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(value = "ProductSearchVO", description = "商品搜索结果")
public class ProductSearchVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SPU ID")
	private Long spuId;

	@ApiModelProperty(value = "SPU名称")
	private String spuName;

	@ApiModelProperty(value = "主图URL")
	private String mainImage;

	@ApiModelProperty(value = "最低价格")
	private BigDecimal minPrice;

	@ApiModelProperty(value = "最高价格")
	private BigDecimal maxPrice;

	@ApiModelProperty(value = "销量")
	private Integer sales;

	@ApiModelProperty(value = "SKU列表")
	private List<SkuInfoVO> skuList;

	@Data
	@ApiModel(value = "SkuInfoVO", description = "SKU信息")
	public static class SkuInfoVO implements Serializable {

		private static final long serialVersionUID = 1L;

		@ApiModelProperty(value = "SKU ID")
		private Long skuId;

		@ApiModelProperty(value = "SKU编码")
		private String skuCode;

		@ApiModelProperty(value = "SKU名称")
		private String skuName;

		@ApiModelProperty(value = "价格")
		private BigDecimal price;

		@ApiModelProperty(value = "库存")
		private Integer stock;

		@ApiModelProperty(value = "规格参数")
		private Map<String, String> specifications;
	}
}

