package com.haalan.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户秒杀记录VO
 */
@Data
@ApiModel(value = "UserSeckillRecordVO", description = "用户秒杀记录视图对象")
public class UserSeckillRecordVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "订单号")
	private String orderNo;

	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	@ApiModelProperty(value = "活动名称")
	private String activityName;

	@ApiModelProperty(value = "商品名称")
	private String productName;

	@ApiModelProperty(value = "商品图片URL")
	private String productImage;

	@ApiModelProperty(value = "秒杀价格")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "购买数量")
	private Integer quantity;

	@ApiModelProperty(value = "订单状态：0-待支付，1-已支付，2-已取消，3-已完成")
	private Integer status;

	@ApiModelProperty(value = "状态名称")
	private String statusName;

	@ApiModelProperty(value = "创建时间")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "支付过期时间")
	private LocalDateTime payExpireTime;
}
