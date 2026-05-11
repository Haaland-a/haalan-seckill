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
	public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
	public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
	public static final String SECKILL_ORDER_ROUTING_KEY = "success";
	public static final String SECKILL_ORDER_DETAIL_KEY = "detail";

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
}
