package com.haalan.item.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 商品SKU表
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_sku")
@ApiModel(value = "TSku对象", description = "商品SKU表")
public class TSku implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "SKU编码")
	@TableField("sku_code")
	private String skuCode;

	@ApiModelProperty(value = "SPU ID")
	@TableField("spu_id")
	private Long spuId;

	@ApiModelProperty(value = "SKU名称")
	@TableField("name")
	private String name;

	@ApiModelProperty(value = "规格属性")
	@TableField("specifications")
	private String specifications;

	@ApiModelProperty(value = "原价")
	@TableField("price")
	private BigDecimal price;

	@ApiModelProperty(value = "促销价")
	@TableField("promotion_price")
	private BigDecimal promotionPrice;

	@ApiModelProperty(value = "库存数量")
	@TableField("stock")
	private Integer stock;

	@ApiModelProperty(value = "销量")
	@TableField("sold_count")
	private Integer soldCount;

	@ApiModelProperty(value = "SKU图片")
	@TableField("images")
	private String images;

	@ApiModelProperty(value = "状态: 0-禁用 1-正常")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;


}
