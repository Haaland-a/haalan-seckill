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

	// ==================== 用户行为日志相关 ====================
	// 交换机
	public static final String SECKILL_LOG_EXCHANGE = "seckill.log.exchange";
	// 队列
	public static final String SECKILL_LOG_QUEUE = "seckill.log.queue";
	// 路由
	public static final String SECKILL_LOG_ROUTING_KEY = "seckill.log";

	// 日志死信交换机
	public static final String SECKILL_LOG_DLX_EXCHANGE = "seckill.log.dlx.exchange";
	// 日志死信队列
	public static final String SECKILL_LOG_DLX_QUEUE = "seckill.log.dlx.queue";
	// 日志死信路由
	public static final String SECKILL_LOG_DLX_ROUTING_KEY = "seckill.log.dlx";

	// ==================== 用户秒杀记录持久化相关 ====================
	// 交换机
	public static final String SECKILL_RECORD_EXCHANGE = "seckill.record.exchange";
	// 队列
	public static final String SECKILL_RECORD_QUEUE = "seckill.record.queue";
	// 路由
	public static final String SECKILL_RECORD_ROUTING_KEY = "seckill.record";

	// 用户秒杀记录死信交换机
	public static final String SECKILL_RECORD_DLX_EXCHANGE = "seckill.record.dlx.exchange";
	// 用户秒杀记录死信队列
	public static final String SECKILL_RECORD_DLX_QUEUE = "seckill.record.dlx.queue";
	// 用户秒杀记录死信路由
	public static final String SECKILL_RECORD_DLX_ROUTING_KEY = "seckill.record.dlx";

	// ==================== 秒杀订单支付状态更新相关 ====================
	// 支付成功交换机
	public static final String SECKILL_PAY_SUCCESS_EXCHANGE = "seckill.pay.success.exchange";
	// 支付成功队列
	public static final String SECKILL_PAY_SUCCESS_QUEUE = "seckill.pay.success.queue";
	// 支付成功路由
	public static final String SECKILL_PAY_SUCCESS_ROUTING_KEY = "seckill.pay.success";

	// 支付成功死信交换机
	public static final String SECKILL_PAY_SUCCESS_DLX_EXCHANGE = "seckill.pay.success.dlx.exchange";
	// 支付成功死信队列
	public static final String SECKILL_PAY_SUCCESS_DLX_QUEUE = "seckill.pay.success.dlx.queue";
	// 支付成功死信路由
	public static final String SECKILL_PAY_SUCCESS_DLX_ROUTING_KEY = "seckill.pay.success.dlx";

	// 订单超时（取消）交换机
	public static final String SECKILL_ORDER_CANCEL_EXCHANGE = "seckill.order.cancel.exchange";
	// 订单超时（取消）队列
	public static final String SECKILL_ORDER_CANCEL_QUEUE = "seckill.order.cancel.queue";
	// 订单超时（取消）路由
	public static final String SECKILL_ORDER_CANCEL_ROUTING_KEY = "seckill.order.cancel";

	// 订单取消死信交换机
	public static final String SECKILL_ORDER_CANCEL_DLX_EXCHANGE = "seckill.order.cancel.dlx.exchange";
	// 订单取消死信队列
	public static final String SECKILL_ORDER_CANCEL_DLX_QUEUE = "seckill.order.cancel.dlx.queue";
	// 订单取消死信路由
	public static final String SECKILL_ORDER_CANCEL_DLX_ROUTING_KEY = "seckill.order.cancel.dlx";

	// ==================== 秒杀订单退款成功相关 ====================
	// 退款成功交换机
	public static final String SECKILL_REFUND_SUCCESS_EXCHANGE = "seckill.refund.success.exchange";
	// 退款成功队列
	public static final String SECKILL_REFUND_SUCCESS_QUEUE = "seckill.refund.success.queue";
	// 退款成功路由
	public static final String SECKILL_REFUND_SUCCESS_ROUTING_KEY = "seckill.refund.success";

	// 退款成功死信交换机
	public static final String SECKILL_REFUND_SUCCESS_DLX_EXCHANGE = "seckill.refund.success.dlx.exchange";
	// 退款成功死信队列
	public static final String SECKILL_REFUND_SUCCESS_DLX_QUEUE = "seckill.refund.success.dlx.queue";
	// 退款成功死信路由
	public static final String SECKILL_REFUND_SUCCESS_DLX_ROUTING_KEY = "seckill.refund.success.dlx";
}

