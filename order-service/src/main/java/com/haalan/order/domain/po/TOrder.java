package com.haalan.order.domain.po;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单主表
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_order")
@ApiModel(value = "TOrder对象", description = "订单主表")
public class TOrder implements Serializable {

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

	@ApiModelProperty(value = "订单总金额")
	@TableField("total_amount")
	private BigDecimal totalAmount;

	@ApiModelProperty(value = "优惠金额")
	@TableField("discount_amount")
	private BigDecimal discountAmount;

	@ApiModelProperty(value = "实付金额")
	@TableField("actual_amount")
	private BigDecimal actualAmount;

	@ApiModelProperty(value = "订单类型: 1-普通 2-秒杀 3-团购")
	@TableField("order_type")
	private Integer orderType;

	@ApiModelProperty(value = "订单状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "收货地址ID")
	@TableField("address_id")
	private Long addressId;

	@ApiModelProperty(value = "收货人信息快照")
	@TableField("receiver_info")
	private String receiverInfo;

	@ApiModelProperty(value = "备注")
	@TableField("remark")
	private String remark;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;


}
