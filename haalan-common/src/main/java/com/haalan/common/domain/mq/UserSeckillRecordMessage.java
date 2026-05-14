package com.haalan.common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户秒杀记录持久化消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSeckillRecordMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 消息ID（用于追踪）
	 */
	private String messageId;

	/**
	 * 订单号
	 */
	private String orderNo;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 活动ID
	 */
	private Long activityId;

	/**
	 * 活动名称
	 */
	private String activityName;

	/**
	 * 商品ID
	 */
	private Long productId;

	/**
	 * 商品名称
	 */
	private String productName;

	/**
	 * 商品图片URL
	 */
	private String productImage;

	/**
	 * 秒杀价格
	 */
	private BigDecimal seckillPrice;

	/**
	 * 购买数量
	 */
	private Integer quantity;

	/**
	 * 订单状态：0-待支付，1-已支付，2-已取消，3-已完成
	 */
	private Integer status;

	/**
	 * 支付过期时间
	 */
	private LocalDateTime payExpireTime;

	/**
	 * 消息创建时间
	 */
	private LocalDateTime createTime;
}
