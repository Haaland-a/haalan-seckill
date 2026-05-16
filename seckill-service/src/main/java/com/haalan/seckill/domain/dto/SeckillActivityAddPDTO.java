package com.haalan.seckill.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 *
 * @author Haaland
 * @description SeckillActivityAddPDTO
 * </p>
 * @date 2026/4/24
 */

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillActivityAddPDTO", description = "添加秒杀活动产品")
public class SeckillActivityAddPDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("skuId")
	@ApiModelProperty(value = "商品SKU ID", required = true)
	@NotNull(message = "商品SKU ID不能为空")
	private Long skuId;
	@JsonProperty("seckillPrice")
	@ApiModelProperty(value = "秒杀价格", required = true)
	@NotNull(message = "秒杀价格不能为空")
	@DecimalMin(value = "0.01", message = "秒杀价格必须大于0")
	private BigDecimal seckillPrice;
	@JsonProperty("stock")
	@ApiModelProperty(value = "库存", required = true)
	@NotNull(message = "库存不能为空")
	@Min(value = 1, message = "库存必须大于0")
	private Integer stock;
	@JsonProperty("limitPerUser")
	@ApiModelProperty(value = "每人限购数量", required = true)
	@NotNull(message = "每人限购数量不能为空")
	@Min(value = 1, message = "每人限购数量必须大于0")
	private Integer limitPerUser;
	@JsonProperty("sort")
	@ApiModelProperty(value = "排序", required = true)
	@NotNull(message = "排序不能为空")
	@Min(value = 0, message = "排序不能小于0")
	private Integer sort;

	@JsonProperty("alipayProductCode")
	@TableField("alipay_product_code")
	private String alipayProductCode;

	@JsonProperty("wechatProductCode")
	@TableField("wechat_product_code")
	private String wechatProductCode;
}
