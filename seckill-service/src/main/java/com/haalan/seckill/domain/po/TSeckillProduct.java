package com.haalan.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀商品表
 * </p>
 *
 * @author haaland
 * @since 2026-04-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_seckill_product")
@ApiModel(value = "TSeckillProduct对象", description = "秒杀商品表")
public class TSeckillProduct implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "活动ID")
	@TableField("activity_id")
	private Long activityId;

	@ApiModelProperty(value = "商品SKU ID")
	@TableField("sku_id")
	private Long skuId;

	@ApiModelProperty(value = "SKU编码")
	@TableField("sku_code")
	private String skuCode;

	@ApiModelProperty(value = "商品名称")
	@TableField("product_name")
	private String productName;

	@ApiModelProperty(value = "原价")
	@TableField("original_price")
	private BigDecimal originalPrice;

	@ApiModelProperty(value = "秒杀价")
	@TableField("seckill_price")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "秒杀库存")
	@TableField("stock")
	private Integer stock;

	@ApiModelProperty(value = "锁定库存")
	@TableField("locked_stock")
	private Integer lockedStock;

	@ApiModelProperty(value = "已售库存")
	@TableField("sold_stock")
	private Integer soldStock;

	@ApiModelProperty(value = "每人限购数量")
	@TableField("limit_per_user")
	private Integer limitPerUser;

	@ApiModelProperty(value = "排序")
	@TableField("sort")
	private Integer sort;

	@ApiModelProperty(value = "乐观锁版本号")
	@TableField("version")
	@Version
	private Integer version;

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
