package com.haalan.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RabbitMQConfig {

	@Resource
	private MQProperties mqProperties;
	// 交换机
	@Bean
	public TopicExchange seckillOrderExchange() {
		return new TopicExchange(RabbitConstants.SECKILL_ORDER_EXCHANGE, true, false);
	}

	// 队列
	@Bean
	public Queue seckillOrderQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_ORDER_QUEUE)
				// 配置死信交换机
				.deadLetterExchange(RabbitConstants.SECKILL_ORDER_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.SECKILL_ORDER_DLX_ROUTINGKEY)
				.build();
	}

	// 绑定关系
	@Bean
	public Binding bindingSeckillOrder() {
		return BindingBuilder.bind(seckillOrderQueue())
				.to(seckillOrderExchange())
				.with(RabbitConstants.SECKILL_ORDER_ROUTING_KEY);
	}

	// 配置消息转换器
	@Bean
	public MessageConverter messageConverter() {
		// 1. 先配置ObjectMapper
		ObjectMapper objectMapper = new ObjectMapper();
		// 支持 LocalDateTime
		objectMapper.registerModule(new JavaTimeModule());
		// 不转时间戳
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// 2. 创建Jackson2JsonMessageConverter并设置自定义的ObjectMapper
		Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter(objectMapper);
		// 配置自动创建消息id
		jackson2JsonMessageConverter.setCreateMessageIds(true);

		return jackson2JsonMessageConverter;
	}

	// 配置RabbitTemplate
	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(messageConverter);

		// 开启发送确认
		rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
			if (correlationData != null) {
				if (ack) {
					log.info("消息发送成功, messageId={}", correlationData.getId());
				} else {
					log.error("消息发送失败, messageId={}, cause={}",
							correlationData.getId(), cause);
				}
			}
		});

		// 开启失败回调
		rabbitTemplate.setReturnsCallback(returned -> {
			log.error("消息路由失败, message={}, replyCode={}, replyText={}, exchange={}, routingKey={}",
					returned.getMessage(), returned.getReplyCode(),
					returned.getReplyText(), returned.getExchange(),
					returned.getRoutingKey());
		});

		return rabbitTemplate;
	}


	// 死信交换机
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_ORDER_DLX_EXCHANGE);
	}

	// 死信队列
	@Bean
	public Queue deadLetterQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_ORDER_DLX_QUEUE).build();
	}

	// 死信绑定
	@Bean
	public Binding deadLetterBinding() {
		return BindingBuilder.bind(deadLetterQueue())
				.to(deadLetterExchange())
				.with(RabbitConstants.SECKILL_ORDER_DLX_ROUTINGKEY);
	}

	// ================================计时器=============================================

	// 订单超时死信
	@Bean
	public DirectExchange orderTimeoutDlxExchange() {
		return new DirectExchange(RabbitConstants.ORDER_TIMEOUT_DLX_EXCHANGE);
	}

	// 订单超时死信队列
// 订单超时死信队列（加死信，兜底）
	@Bean
	public Queue orderTimeoutDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.ORDER_TIMEOUT_DLX_QUEUE)
				.deadLetterExchange(RabbitConstants.ORDER_TIMEOUT_BACKUP_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.ORDER_TIMEOUT_BACKUP_DLX_ROUTING_KEY)
				.build();
	}

	// 订单超时绑定
	@Bean
	public Binding orderTimeoutDlxBinding() {
		return BindingBuilder.bind(orderTimeoutDlxQueue())
				.to(orderTimeoutDlxExchange())
				.with(RabbitConstants.ORDER_TIMEOUT_DLX_ROUTING_KEY);
	}

	/**
	 * 延时队列（消息在这里等待15分钟后过期，自动转到死信队列）
	 */
	@Bean
	public Queue orderTimeoutQueue() {
		Map<String, Object> args = new HashMap<>();
		// 消息过期后发送到死信交换机
		args.put("x-dead-letter-exchange", RabbitConstants.ORDER_TIMEOUT_DLX_EXCHANGE);
		args.put("x-dead-letter-routing-key", RabbitConstants.ORDER_TIMEOUT_DLX_ROUTING_KEY);
		// 消息过期时间：15分钟 = 900000毫秒
		args.put("x-message-ttl", mqProperties.getTimeOut());
		return new Queue(RabbitConstants.ORDER_TIMEOUT_QUEUE, true, false, false, args);
	}

	// ==================== 订单超时死信队列（失败后转到备用死信） ====================

	// 订单超时备用死信交换机
	@Bean
	public DirectExchange orderTimeoutBackupDlxExchange() {
		return new DirectExchange(RabbitConstants.ORDER_TIMEOUT_BACKUP_DLX_EXCHANGE);
	}

	// 订单超时备用死信队列（人工处理 / 告警）
	@Bean
	public Queue orderTimeoutBackupDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.ORDER_TIMEOUT_BACKUP_DLX_QUEUE).build();
	}

	// 备用死信绑定
	@Bean
	public Binding orderTimeoutBackupDlxBinding() {
		return BindingBuilder.bind(orderTimeoutBackupDlxQueue())
				.to(orderTimeoutBackupDlxExchange())
				.with(RabbitConstants.ORDER_TIMEOUT_BACKUP_DLX_ROUTING_KEY);
	}

	// ==================== 用户行为日志相关配置 ====================

	/**
	 * 日志交换机（Topic类型，支持灵活路由）
	 */
	@Bean
	public TopicExchange seckillLogExchange() {
		return new TopicExchange(RabbitConstants.SECKILL_LOG_EXCHANGE, true, false);
	}

	/**
	 * 日志队列（带死信，处理失败的消息可以重发或人工处理）
	 */
	@Bean
	public Queue seckillLogQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_LOG_QUEUE)
				.deadLetterExchange(RabbitConstants.SECKILL_LOG_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.SECKILL_LOG_DLX_ROUTING_KEY)
				.build();
	}

	/**
	 * 日志交换机与队列绑定
	 */
	@Bean
	public Binding seckillLogBinding() {
		return BindingBuilder.bind(seckillLogQueue())
				.to(seckillLogExchange())
				.with(RabbitConstants.SECKILL_LOG_ROUTING_KEY);
	}

	/**
	 * 日志死信交换机
	 */
	@Bean
	public DirectExchange seckillLogDlxExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_LOG_DLX_EXCHANGE, true, false);
	}

	/**
	 * 日志死信队列（处理失败的消息会到这里，可以重发或人工处理）
	 */
	@Bean
	public Queue seckillLogDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_LOG_DLX_QUEUE).build();
	}

	/**
	 * 日志死信绑定
	 */
	@Bean
	public Binding seckillLogDlxBinding() {
		return BindingBuilder.bind(seckillLogDlxQueue())
				.to(seckillLogDlxExchange())
				.with(RabbitConstants.SECKILL_LOG_DLX_ROUTING_KEY);
	}

	// ==================== 用户秒杀记录持久化相关配置 ====================

	/**
	 * 用户秒杀记录交换机
	 */
	@Bean
	public DirectExchange seckillRecordExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_RECORD_EXCHANGE, true, false);
	}

	/**
	 * 用户秒杀记录队列（带死信）
	 */
	@Bean
	public Queue seckillRecordQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_RECORD_QUEUE)
				.deadLetterExchange(RabbitConstants.SECKILL_RECORD_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.SECKILL_RECORD_DLX_ROUTING_KEY)
				.build();
	}

	/**
	 * 用户秒杀记录交换机与队列绑定
	 */
	@Bean
	public Binding seckillRecordBinding() {
		return BindingBuilder.bind(seckillRecordQueue())
				.to(seckillRecordExchange())
				.with(RabbitConstants.SECKILL_RECORD_ROUTING_KEY);
	}

	/**
	 * 用户秒杀记录死信交换机
	 */
	@Bean
	public DirectExchange seckillRecordDlxExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_RECORD_DLX_EXCHANGE, true, false);
	}

	/**
	 * 用户秒杀记录死信队列（处理失败的消息会到这里，可以重发或人工处理）
	 */
	@Bean
	public Queue seckillRecordDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_RECORD_DLX_QUEUE).build();
	}

	/**
	 * 用户秒杀记录死信绑定
	 */
	@Bean
	public Binding seckillRecordDlxBinding() {
		return BindingBuilder.bind(seckillRecordDlxQueue())
				.to(seckillRecordDlxExchange())
				.with(RabbitConstants.SECKILL_RECORD_DLX_ROUTING_KEY);
	}

	// ==================== 秒杀订单支付状态更新相关配置 ====================

	/**
	 * 支付成功交换机
	 */
	@Bean
	public DirectExchange seckillPaySuccessExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_PAY_SUCCESS_EXCHANGE, true, false);
	}

	/**
	 * 支付成功死信交换机
	 */
	@Bean
	public DirectExchange seckillPaySuccessDlxExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_PAY_SUCCESS_DLX_EXCHANGE, true, false);
	}

	/**
	 * 支付成功队列（带死信）
	 */
	@Bean
	public Queue seckillPaySuccessQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_PAY_SUCCESS_QUEUE)
				.deadLetterExchange(RabbitConstants.SECKILL_PAY_SUCCESS_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.SECKILL_PAY_SUCCESS_DLX_ROUTING_KEY)
				.build();
	}

	/**
	 * 支付成功死信队列
	 */
	@Bean
	public Queue seckillPaySuccessDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_PAY_SUCCESS_DLX_QUEUE).build();
	}

	/**
	 * 支付成功绑定
	 */
	@Bean
	public Binding seckillPaySuccessBinding() {
		return BindingBuilder.bind(seckillPaySuccessQueue())
				.to(seckillPaySuccessExchange())
				.with(RabbitConstants.SECKILL_PAY_SUCCESS_ROUTING_KEY);
	}

	/**
	 * 支付成功死信绑定
	 */
	@Bean
	public Binding seckillPaySuccessDlxBinding() {
		return BindingBuilder.bind(seckillPaySuccessDlxQueue())
				.to(seckillPaySuccessDlxExchange())
				.with(RabbitConstants.SECKILL_PAY_SUCCESS_DLX_ROUTING_KEY);
	}

	/**
	 * 订单取消（超时）交换机
	 */
	@Bean
	public DirectExchange seckillOrderCancelExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_ORDER_CANCEL_EXCHANGE, true, false);
	}

	/**
	 * 订单取消死信交换机
	 */
	@Bean
	public DirectExchange seckillOrderCancelDlxExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_ORDER_CANCEL_DLX_EXCHANGE, true, false);
	}

	/**
	 * 订单取消（超时）队列（带死信）
	 */
	@Bean
	public Queue seckillOrderCancelQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_ORDER_CANCEL_QUEUE)
				.deadLetterExchange(RabbitConstants.SECKILL_ORDER_CANCEL_DLX_EXCHANGE)
				.deadLetterRoutingKey(RabbitConstants.SECKILL_ORDER_CANCEL_DLX_ROUTING_KEY)
				.build();
	}

	/**
	 * 订单取消死信队列
	 */
	@Bean
	public Queue seckillOrderCancelDlxQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_ORDER_CANCEL_DLX_QUEUE).build();
	}

	/**
	 * 订单取消（超时）绑定
	 */
	@Bean
	public Binding seckillOrderCancelBinding() {
		return BindingBuilder.bind(seckillOrderCancelQueue())
				.to(seckillOrderCancelExchange())
				.with(RabbitConstants.SECKILL_ORDER_CANCEL_ROUTING_KEY);
	}

	/**
	 * 订单取消死信绑定
	 */
	@Bean
	public Binding seckillOrderCancelDlxBinding() {
		return BindingBuilder.bind(seckillOrderCancelDlxQueue())
				.to(seckillOrderCancelDlxExchange())
				.with(RabbitConstants.SECKILL_ORDER_CANCEL_DLX_ROUTING_KEY);
	}
}
