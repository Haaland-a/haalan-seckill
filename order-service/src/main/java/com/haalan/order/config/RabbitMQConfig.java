package com.haalan.order.config;


import com.haalan.order.mqcallback.MqConfirmCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

	// 交换机
	@Bean
	public TopicExchange seckillOrderExchange() {
		return new TopicExchange(RabbitConstants.SECKILL_ORDER_EXCHANGE, true, false);
	}

	// 队列
	@Bean
	public Queue seckillOrderQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_ORDER_QUEUE)
				.build();
	}

	// 绑定关系
	@Bean
	public Binding bindingSeckillOrder() {
		return BindingBuilder.bind(seckillOrderQueue())
				.to(seckillOrderExchange())
				.with(RabbitConstants.SECKILL_ORDER_ROUTING_KEY);
	}

	@Bean
	public Binding bindingDetail() {
		return BindingBuilder.bind(seckillOrderQueue())
				.to(seckillOrderExchange())
				.with(RabbitConstants.SECKILL_ORDER_DETAIL_KEY);
	}


	// 配置消息转换器
	@Bean
	public Jackson2JsonMessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	// 配置RabbitTemplate
	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(messageConverter());

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

	@Bean
	// 添加MqConfirmCallback, 两个参数只要收到消息就会删除那个redis存的无用信息
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
										 MqConfirmCallback confirmCallback) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setConfirmCallback(confirmCallback);
		// ...其他配置
		return rabbitTemplate;
	}
}