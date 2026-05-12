package com.haalan.common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 消息ID（用于追踪）
	 */
	private String messageId;

	/**
	 * 预订单号
	 */
	private String preOrderNo;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 请求ID（用于幂等）
	 */
	private String requestId;

	/**
	 * 活动ID
	 */
	private Long activityId;

	/**
	 * 商品sku
	 */
	private Long skuId;

	/**
	 * 秒杀商品ID
	 */
	private Long seckillProductId;

	/**
	 * 购买数量
	 */
	private Integer quantity;

	/**
	 * 秒杀价格
	 */
	private BigDecimal seckillPrice;

	/**
	 * 订单总金额
	 */
	private BigDecimal totalAmount;

	/**
	 * 消息创建时间
	 */
	private LocalDateTime createTime;
}