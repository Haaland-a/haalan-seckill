package com.haalan.order.listener;

import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.utils.UserContext;
import com.haalan.order.config.RabbitConstants;
import com.haalan.order.service.ITSeckillOrder0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor

public class OrderTimeoutListener {


	private final ITSeckillOrder0Service seckillOrderService;

	/**
	 * 监听超时死信队列 - 订单超时后会自动到达这里
	 */
	@RabbitListener(queues = RabbitConstants.ORDER_TIMEOUT_DLX_QUEUE)
	public void handleTimeoutOrder(OrderTimeoutMessage message) {
		log.info("消费者接收到消息：【{}】", message);

		// 关键：从消息中获取userId，设置到ThreadLocal
		UserContext.setUser(message.getUserId());

		try {
			// 业务处理，内部可以通过 UserContext.getUser() 获取用户ID
			seckillOrderService.setStatus(message);

		} catch (Exception e) {
			log.error("处理秒杀订单失败: orderNo={}", message.getOrderNo(), e);
			throw e; // 重新抛出，让MQ重试
		} finally {
			// 清理，防止内存泄漏
			UserContext.removeUser();
		}
	}
}