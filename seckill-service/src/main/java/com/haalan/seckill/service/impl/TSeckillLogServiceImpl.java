package com.haalan.seckill.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.seckill.config.RabbitConstants;
import com.haalan.seckill.domain.po.SeckillUserLogMessage;
import com.haalan.seckill.domain.po.TSeckillLog;
import com.haalan.seckill.mapper.TSeckillLogMapper;
import com.haalan.seckill.service.ITSeckillLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀日志表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-12
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TSeckillLogServiceImpl extends ServiceImpl<TSeckillLogMapper, TSeckillLog> implements ITSeckillLogService {

	private final RabbitTemplate rabbitTemplate;

	@Async("seckillLogExecutor")
	@Override
	public void logToMq(Long userId, Long activityId, Long skuId, Long productId,
						String action, String failReason, Integer costTime, String ip, String userAgent) {
		try {


			// 构建日志消息
			SeckillUserLogMessage logMessage = SeckillUserLogMessage.builder()
					.userId(userId)
					.activityId(activityId)
					.skuId(skuId)
					.productId(productId)
					.action(action)
					.ip(ip)
					.userAgent(userAgent)
					.failReason(failReason)
					.costTime(costTime)
					.createTime(LocalDateTime.now())
					.build();

			// 发送日志到MQ
			rabbitTemplate.convertAndSend(
					RabbitConstants.SECKILL_LOG_EXCHANGE,
					RabbitConstants.SECKILL_LOG_ROUTING_KEY,
					logMessage
			);

			log.debug("用户秒杀行为日志已发送, userId={}, action={}",
					userId, action);

		} catch (Exception e) {
			// 日志记录失败不影响主流程
			log.error("发送用户行为日志失败, userId={}, activityId={}",
					userId, activityId, e);
		}
	}
}
