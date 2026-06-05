package com.haalan.api.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * SKU详情VO
 * </p>
 *
 * @author Haaland
 * @date 2026/5/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SkuDetailVO", description = "SKU详情VO")
public class SkuDetailVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("SKU ID")
	private Long skuId;

	@ApiModelProperty("SPU ID")
	private Long spuId;

	@ApiModelProperty("SKU编码")
	private String skuCode;

	@ApiModelProperty("SKU名称")
	private String skuName;

	@ApiModelProperty("SKU图片")
	private String images;

	@ApiModelProperty("销售价格")
	private BigDecimal price;

	@ApiModelProperty("规格参数")
	private String specifications;

	@ApiModelProperty("库存数量")
	private Integer stock;

	@ApiModelProperty("状态：0-下架，1-上架")
	private Integer status;
}
