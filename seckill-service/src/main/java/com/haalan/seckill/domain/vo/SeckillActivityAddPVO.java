package com.haalan.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 *
 * @author Haaland
 * @description SeckillActivityAddPVO
 * </p>
 * @date 2026/4/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillActivityCreateResultVO", description = "创建秒杀活动响应")
public class SeckillActivityAddPVO {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动商品ID")
	private Long seckillProductId;
}
