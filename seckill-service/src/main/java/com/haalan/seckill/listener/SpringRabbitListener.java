package com.haalan.seckill.listener;

import com.haalan.common.domain.mq.UserSeckillRecordMessage;
import com.haalan.common.utils.BeanUtils;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.config.RabbitConstants;
import com.haalan.seckill.domain.po.SeckillUserLogMessage;
import com.haalan.seckill.domain.po.TSeckillLog;
import com.haalan.seckill.domain.po.UserSeckillRecord;
import com.haalan.seckill.service.ITSeckillLogService;
import com.haalan.seckill.service.IUserSeckillRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpringRabbitListener {

	private final ITSeckillLogService seckillLogService;

	private final IUserSeckillRecordService userSeckillRecordService;

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

	/**
	 * 监听用户秒杀记录消息，持久化到数据库
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_RECORD_QUEUE)
	public void listenUserSeckillRecordMessage(UserSeckillRecordMessage message) {
		log.info("接收到用户秒杀记录消息：【{}】", message);

		try {
			// 转换为实体类
			UserSeckillRecord record = BeanUtils.toBean(message, UserSeckillRecord.class);

			// 保存（内部会自动分表）
			userSeckillRecordService.saveUserRecord(record);

			log.info("用户秒杀记录保存成功, userId={}, orderNo={}",
					message.getUserId(), message.getOrderNo());

		} catch (Exception e) {
			log.error("保存用户秒杀记录失败, userId={}, orderNo={}",
					message.getUserId(), message.getOrderNo(), e);
			throw e; // 重新抛出，让MQ重试
		}
	}
}