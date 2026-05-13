package com.haalan.order.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀订单表
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)

@Builder
@ApiModel(value = "TSeckillOrder0对象", description = "秒杀订单表")
public class TSeckillOrder implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "订单号")
	@TableField("order_no")
	private String orderNo;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "活动ID")
	@TableField("activity_id")
	private Long activityId;

	@ApiModelProperty(value = "秒杀商品ID")
	@TableField("seckill_product_id")
	private Long seckillProductId;

	@ApiModelProperty(value = "SKU ID")
	@TableField("sku_id")
	private Long skuId;

	@ApiModelProperty(value = "商品名称")
	@TableField("product_name")
	private String productName;

	@ApiModelProperty(value = "秒杀价格")
	@TableField("seckill_price")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "购买数量")
	@TableField("quantity")
	private Integer quantity;

	@ApiModelProperty(value = "订单总金额")
	@TableField("total_amount")
	private BigDecimal totalAmount;

	@ApiModelProperty(value = "订单状态: 0-待支付 1-已支付 2-已取消 3-已超时")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "下单时间")
	@TableField("order_time")
	private LocalDateTime orderTime;

	@ApiModelProperty(value = "支付时间")
	@TableField("pay_time")
	private LocalDateTime payTime;

	@ApiModelProperty(value = "取消时间")
	@TableField("cancel_time")
	private LocalDateTime cancelTime;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;

	@TableField("alipay_product_code")
	private String alipayProductCode;

	@TableField("wechat_product_code")
	private String wechatProductCode;
}
