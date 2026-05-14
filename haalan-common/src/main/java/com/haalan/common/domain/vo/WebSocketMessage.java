package com.haalan.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebSocket消息体（通用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 消息类型
	 * ORDER_STATUS_CHANGE - 订单状态变更
	 * SECKILL_RESULT - 秒杀结果
	 * PAYMENT_SUCCESS - 支付成功
	 * STOCK_CHANGE - 库存变化
	 * SYSTEM_NOTICE - 系统通知
	 */
	private String messageType;

	/**
	 * 消息标题
	 */
	private String title;

	/**
	 * 消息内容
	 */
	private String content;

	/**
	 * 业务数据（JSON格式）
	 */
	private Object data;

	/**
	 * 消息时间
	 */
	private LocalDateTime timestamp;
}
