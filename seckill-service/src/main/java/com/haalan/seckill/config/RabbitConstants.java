package com.haalan.seckill.config;

/**
 * <p>
 *
 * @author Haaland
 * @description RabbitConstants
 * </p>
 * @date 2026/5/11
 */

public class RabbitConstants {
	// 交换机
	public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
	// 队列
	public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
	// 路由
	public static final String SECKILL_ORDER_ROUTING_KEY = "success";

	/**
	 * 待确认消息记录Key前缀
	 * seckill:pending:msg:{messageId}
	 */
	public static final String SECKILL_PENDING_MSG_PREFIX = "seckill:pending:msg:";
	/**
	 * 待确认消息的有效期（10分钟）
	 */
	public static final long PENDING_MSG_EXPIRE_MINUTES = 10;
	/**
	 * 消息补偿扫描的间隔（30秒）
	 */
	public static final long PENDING_MSG_SCAN_INTERVAL_SECONDS = 30;
	// 死信 交换机
	public static final String SECKILL_ORDER_DLX_EXCHANGE = "seckill.order.dlx.exchange";
	// 死信队列
	public static final String SECKILL_ORDER_DLX_QUEUE = "seckill.order.dlx.queue";
	// 死信队列路由
	public static final String SECKILL_ORDER_DLX_ROUTINGKEY = "seckill.order.dlx.routingkey";

	// 订单超时队列
	public static final String ORDER_TIMEOUT_QUEUE = "seckill.order.timeout.queue";

	// 超时后转到的死信
	public static final String ORDER_TIMEOUT_DLX_EXCHANGE = "seckill.timeout.dlx.exchange";
	public static final String ORDER_TIMEOUT_DLX_QUEUE = "seckill.timeout.dlx.queue";
	public static final String ORDER_TIMEOUT_DLX_ROUTING_KEY = "seckill.timeout.dlx";

	// 超时死信的备用死信
	public static final String ORDER_TIMEOUT_BACKUP_DLX_EXCHANGE = "seckill.timeout.backup.dlx.exchange";
	public static final String ORDER_TIMEOUT_BACKUP_DLX_QUEUE = "seckill.timeout.backup.dlx.queue";
	public static final String ORDER_TIMEOUT_BACKUP_DLX_ROUTING_KEY = "seckill.timeout.backup.dlx";
}
