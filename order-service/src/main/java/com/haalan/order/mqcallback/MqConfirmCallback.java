package com.haalan.order.mqcallback;


import com.haalan.order.config.RabbitConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqConfirmCallback implements RabbitTemplate.ConfirmCallback {

	private final StringRedisTemplate redisTemplate;

	@Override
	public void confirm(CorrelationData correlationData, boolean ack, String cause) {
		if (correlationData == null) {
			return;
		}

		if (ack) {
			// 发送成功，删除Redis中的待确认记录
			String pendingMsgKey = RabbitConstants.SECKILL_PENDING_MSG_PREFIX
					+ correlationData.getId();
			redisTemplate.delete(pendingMsgKey);
			log.info("MQ消息已确认，删除待确认记录, messageId={}", correlationData.getId());
		} else {
			// 发送失败，保留Redis记录等待补偿
			log.error("MQ消息发送失败, messageId={}, reason={}", correlationData.getId(), cause);
		}
	}
}