package com.haalan.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * <p>
 *
 * @author Haaland
 * @description ProductStringDTO
 * </p>
 * @date 2026/4/24
 */

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "ProductStringDTO", description = "传递参数")

public class ProductStringDTO {

	private static final long serialVersionUID = 1L;

	@JsonProperty("skuCode")
	@ApiModelProperty(value = "商品Code", required = true)
	private String skuCode;

	@JsonProperty("productName")
	@ApiModelProperty(value = "商品名称", required = true)
	private String productName;

	@JsonProperty("originalPrice")
	@ApiModelProperty(value = "商品原价", required = true)
	private BigDecimal originalPrice;

	@JsonProperty("stock")
	@ApiModelProperty(value = "商品库存", required = true)
	private Integer stock;

	@JsonProperty("alipayProductCode")
	@ApiModelProperty(value = "支付宝产品编码")
	private String alipayProductCode;

	@JsonProperty("wechatProductCode")
	@ApiModelProperty(value = "微信产品编码")
	private String wechatProductCode;

}
