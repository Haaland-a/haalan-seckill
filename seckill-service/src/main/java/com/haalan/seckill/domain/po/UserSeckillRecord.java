package com.haalan.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
 * 用户秒杀记录表（支持分表）
 * </p>
 *
 * @author haaland
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "UserSeckillRecord对象", description = "用户秒杀记录表")
public class UserSeckillRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "主键ID")
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

	@ApiModelProperty(value = "活动名称")
	@TableField("activity_name")
	private String activityName;

	@ApiModelProperty(value = "商品ID")
	@TableField("product_id")
	private Long productId;

	@ApiModelProperty(value = "商品名称")
	@TableField("product_name")
	private String productName;

	@ApiModelProperty(value = "商品图片URL")
	@TableField("product_image")
	private String productImage;

	@ApiModelProperty(value = "秒杀价格")
	@TableField("seckill_price")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "购买数量")
	@TableField("quantity")
	private Integer quantity;

	@ApiModelProperty(value = "订单状态：0-待支付，1-已支付，2-已取消，3-已完成")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "支付过期时间")
	@TableField("pay_expire_time")
	private LocalDateTime payExpireTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;

	@ApiModelProperty(value = "逻辑删除：0-未删除，1-已删除")
	@TableField("is_deleted")
	@TableLogic
	private Integer isDeleted;


}
