package com.haalan.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * <p>
 *
 * @author Haaland
 * @description SeckillProductSkuDTo
 * </p>
 * @date 2026/4/27
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillProductSkuDTO {

	@JsonProperty("id")
	@ApiModelProperty(value = "ID", required = true)
	@NotNull(message = "ID不能为空")
	private Long id;
	@JsonProperty("skuId")
	@ApiModelProperty(value = "商品SKU ID", required = true)
	@NotNull(message = "商品SKU ID不能为空")
	private Long skuId;
}
