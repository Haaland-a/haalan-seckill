package com.haalan.cart.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 购物车明细表
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_cart_item")
@ApiModel(value = "TCartItem对象", description = "购物车明细表")
public class TCartItem implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "购物车项ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "SKU ID")
	@TableField("sku_id")
	private Long skuId;

	@ApiModelProperty(value = "SPU ID（冗余，便于按商品聚合）")
	@TableField("spu_id")
	private Long spuId;

	@ApiModelProperty(value = "数量")
	@TableField("quantity")
	private Integer quantity;

	@ApiModelProperty(value = "是否勾选：0-未选，1-已选")
	@TableField("selected")
	private Integer selected;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;

}
