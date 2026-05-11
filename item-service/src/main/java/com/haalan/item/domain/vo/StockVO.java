package com.haalan.item.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "库存信息VO")
public class StockVO {

	@ApiModelProperty("SKU ID")
	private Long skuId;

	@ApiModelProperty("库存数量")
	private Integer stock;

	@ApiModelProperty("状态：0-缺货，1-有货")
	private Integer status;

	@ApiModelProperty("状态名称")
	private String statusName;
}
