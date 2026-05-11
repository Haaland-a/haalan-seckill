package com.haalan.seckill.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单DTO
 */
@Data
@ApiModel(value = "SeckillOrderDTO", description = "秒杀订单DTO")
@Builder
public class SeckillOrderDTO {

	/**
	 * 订单号
	 */
	@NotBlank(message = "订单号不能为空")
	@Size(max = 32, message = "订单号长度不能超过32")
	@ApiModelProperty(value = "订单号")
	private String orderNo;

	/**
	 * 用户ID
	 */
	@NotNull(message = "用户ID不能为空")
	@ApiModelProperty(value = "用户ID")
	private Long userId;

	/**
	 * 活动ID
	 */
	@NotNull(message = "活动ID不能为空")
	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	/**
	 * 秒杀商品ID
	 */
	@NotNull(message = "秒杀商品ID不能为空")
	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;

	/**
	 * SKU ID
	 */
	@NotNull(message = "SKU ID不能为空")
	@ApiModelProperty(value = "SKU ID")
	private Long skuId;

	/**
	 * 秒杀价格
	 */
	@NotNull(message = "秒杀价格不能为空")
	@DecimalMin(value = "0.01", message = "秒杀价格必须大于0")
	@ApiModelProperty(value = "秒杀价格")
	private BigDecimal seckillPrice;

	/**
	 * 购买数量
	 */
	@NotNull(message = "购买数量不能为空")
	@Min(value = 1, message = "购买数量不能小于1")
	@ApiModelProperty(value = "购买数量")
	private Integer quantity;

	/**
	 * 订单总金额
	 */
	@NotNull(message = "订单总金额不能为空")
	@DecimalMin(value = "0.01", message = "订单总金额必须大于0")
	@ApiModelProperty(value = "订单总金额")
	private BigDecimal totalAmount;

	/**
	 * 订单状态
	 * 0-待支付
	 * 1-已支付
	 * 2-已取消
	 * 3-已超时
	 */
	@NotNull(message = "订单状态不能为空")
	@Min(value = 0, message = "订单状态不合法")
	@Max(value = 3, message = "订单状态不合法")
	@ApiModelProperty(value = "订单状态")
	private Integer status;

	/**
	 * 下单时间
	 */
	@NotNull(message = "下单时间不能为空")
	@ApiModelProperty(value = "下单时间")
	private LocalDateTime orderTime;


}