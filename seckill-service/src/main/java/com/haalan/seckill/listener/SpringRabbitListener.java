package com.haalan.seckill.listener;

import com.haalan.common.utils.BeanUtils;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.config.RabbitConstants;
import com.haalan.seckill.domain.po.SeckillUserLogMessage;
import com.haalan.seckill.domain.po.TSeckillLog;
import com.haalan.seckill.service.ITSeckillLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpringRabbitListener {

	private final ITSeckillLogService seckillLogService;

	@RabbitListener(queues = RabbitConstants.SECKILL_LOG_QUEUE)
	public void listenSimpleQueueMessage(SeckillUserLogMessage message) {
		log.info("消费者接收到消息：【{}】", message);

		// 关键：从消息中获取userId，设置到ThreadLocal
		UserContext.setUser(message.getUserId());

		try {
			// 业务处理，内部可以通过 UserContext.getUser() 获取用户ID\
			log.info("保存用户秒杀行为日志{}", message.getUserId());
			TSeckillLog log1 = BeanUtils.toBean(message, TSeckillLog.class);
			seckillLogService.save(log1);

		} catch (Exception e) {
			log.error("处理日志失败{}", message.getUserId(), e);
			throw e; // 重新抛出，让MQ重试
		} finally {
			// 清理，防止内存泄漏
			UserContext.removeUser();
		}
	}
}