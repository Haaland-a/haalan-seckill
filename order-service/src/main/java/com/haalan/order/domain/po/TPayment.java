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
 * 支付表
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_payment")
@ApiModel(value = "TPayment对象", description = "支付表")
public class TPayment implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "订单号")
	@TableField("order_no")
	private String orderNo;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "支付金额")
	@TableField("pay_amount")
	private BigDecimal payAmount;

	@ApiModelProperty(value = "支付方式")
	@TableField("pay_type")
	private Integer payType;

	@ApiModelProperty(value = "支付状态")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "第三方流水")
	@TableField("transaction_id")
	private String transactionId;

	@ApiModelProperty(value = "支付时间")
	@TableField("pay_time")
	private LocalDateTime payTime;


}
