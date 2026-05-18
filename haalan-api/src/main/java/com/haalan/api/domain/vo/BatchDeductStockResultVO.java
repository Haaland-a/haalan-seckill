package com.haalan.api.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>
 * 批量扣减库存结果VO
 * </p>
 *
 * @author Haaland
 * @date 2026/5/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "BatchDeductStockResultVO", description = "批量扣减库存结果")
public class BatchDeductStockResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU ID")
	private Long skuId;

	@ApiModelProperty(value = "是否成功")
	private Boolean success;

	@ApiModelProperty(value = "失败原因")
	private String failReason;
}
