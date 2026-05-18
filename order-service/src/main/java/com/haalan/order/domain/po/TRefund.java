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
 * 退款申请表
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_refund")
@ApiModel(value = "TRefund对象", description = "退款申请表")
public class TRefund implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "退款单号")
	@TableField("refund_no")
	private String refundNo;

	@ApiModelProperty(value = "订单号")
	@TableField("order_no")
	private String orderNo;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "退款金额")
	@TableField("refund_amount")
	private BigDecimal refundAmount;

	@ApiModelProperty(value = "退款原因")
	@TableField("refund_reason")
	private String refundReason;

	@ApiModelProperty(value = "退款状态: 0-处理中 1-退款成功 2-退款失败 3-已拒绝")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "支付宝交易号")
	@TableField("trade_no")
	private String tradeNo;

	@ApiModelProperty(value = "支付宝退款流水号")
	@TableField("refund_trade_no")
	private String refundTradeNo;

	@ApiModelProperty(value = "拒绝原因")
	@TableField("reject_reason")
	private String rejectReason;

	@ApiModelProperty(value = "申请时间")
	@TableField("apply_time")
	private LocalDateTime applyTime;

	@ApiModelProperty(value = "处理时间")
	@TableField("handle_time")
	private LocalDateTime handleTime;

	@ApiModelProperty(value = "完成时间")
	@TableField("complete_time")
	private LocalDateTime completeTime;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;
}
