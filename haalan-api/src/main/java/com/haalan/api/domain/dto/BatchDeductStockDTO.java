package com.haalan.api.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 * 批量扣减库存DTO
 * </p>
 *
 * @author Haaland
 * @date 2026/5/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "BatchDeductStockDTO", description = "批量扣减库存请求参数")
public class BatchDeductStockDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU ID", required = true)
	@NotNull(message = "SKU ID不能为空")
	private Long skuId;

	@ApiModelProperty(value = "扣减数量", required = true)
	@NotNull(message = "扣减数量不能为空")
	private Integer stock;
}
