package com.haalan.order.config;

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

	// ==================== 秒杀订单退款成功相关 ====================

	// 退款成功交换机
	@Bean
	public DirectExchange seckillRefundSuccessExchange() {
		return new DirectExchange(RabbitConstants.SECKILL_REFUND_SUCCESS_EXCHANGE, true, false);
	}
}
