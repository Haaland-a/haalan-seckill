package com.haalan.seckill.listener;

import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderPaySuccessMessage;
import com.haalan.common.domain.mq.SeckillOrderRefundSuccessMessage;
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

	private final com.haalan.seckill.service.ITSeckillProductService seckillProductService;

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

	/**
	 * 监听订单支付成功消息，更新秒杀记录状态为已支付
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_PAY_SUCCESS_QUEUE)
	public void listenPaySuccessMessage(SeckillOrderPaySuccessMessage message) {
		log.info("接收到订单支付成功消息：【{}】", message);

		try {
			// 1. 更新秒杀记录状态为已支付（status=1）- 支持分表
			boolean updated = userSeckillRecordService.updateStatusByOrderNo(
					message.getOrderNo(),
					message.getUserId(),
					1 // 1-已支付
			);

			if (updated) {
				log.info("秒杀记录状态更新为已支付成功, orderNo={}, userId={}",
						message.getOrderNo(), message.getUserId());
			} else {
				log.warn("秒杀记录状态更新失败，可能记录不存在, orderNo={}", message.getOrderNo());
			}

			// 2. 同步扣减数据库中的秒杀商品库存（Redis中已扣减，现在同步到数据库）
			if (message.getSeckillProductId() != null && message.getQuantity() != null) {
				seckillProductService.deductStockAfterPayment(
						message.getSeckillProductId(),
						message.getQuantity()
				);
				log.info("秒杀商品数据库库存扣减成功, seckillProductId={}, quantity={}",
						message.getSeckillProductId(), message.getQuantity());
			} else {
				log.warn("秒杀商品ID或数量为空，跳过数据库库存扣减, orderNo={}", message.getOrderNo());
			}

		} catch (Exception e) {
			log.error("处理支付成功消息失败, orderNo={}, userId={}",
					message.getOrderNo(), message.getUserId(), e);
			throw e; // 重新抛出，让MQ重试
		}
	}

	/**
	 * 监听订单超时取消消息，更新秒杀记录状态为已取消
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_ORDER_CANCEL_QUEUE)
	public void listenOrderCancelMessage(OrderTimeoutMessage message) {
		log.info("接收到订单超时取消消息：【{}】", message);

		try {
			// 先查询当前状态（支持分表）
			UserSeckillRecord record = userSeckillRecordService.getByOrderNo(message.getOrderNo(),
					message.getUserId());

			// 校验：记录不存在或状态不是待支付，则不处理
			if (record == null) {
				log.warn("秒杀记录不存在, orderNo={}", message.getOrderNo());
				return;
			}

			if (record.getStatus() != 0) {
				log.info("订单状态不是待支付，无需取消, orderNo={}, currentStatus={}",
						message.getOrderNo(), record.getStatus());
				return;
			}

			// 更新秒杀记录状态为已取消（status=2）- 支持分表
			boolean updated = userSeckillRecordService.updateStatusByOrderNo(
					message.getOrderNo(),
					message.getUserId(),
					2 // 2-已取消
			);

			if (updated) {
				log.info("秒杀记录状态更新为已取消成功, orderNo={}, userId={}",
						message.getOrderNo(), message.getUserId());
			} else {
				log.warn("秒杀记录状态更新失败，可能记录不存在, orderNo={}", message.getOrderNo());
			}

		} catch (Exception e) {
			log.error("更新秒杀记录取消状态失败, orderNo={}, userId={}",
					message.getOrderNo(), message.getUserId(), e);
			throw e; // 重新抛出，让MQ重试
		}
	}

	/**
	 * 监听订单退款成功消息，回滚数据库库存
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_REFUND_SUCCESS_QUEUE)
	public void listenRefundSuccessMessage(SeckillOrderRefundSuccessMessage message) {
		log.info("接收到订单退款成功消息：【{}】", message);

		try {
			// 回滚数据库中的秒杀商品库存（因为Redis已经回滚了）
			if (message.getSeckillProductId() != null && message.getQuantity() != null) {
				seckillProductService.rollbackStockAfterRefund(
						message.getSeckillProductId(),
						message.getQuantity()
				);
				log.info("秒杀商品数据库库存回滚成功, seckillProductId={}, quantity={}",
						message.getSeckillProductId(), message.getQuantity());
			} else {
				log.warn("秒杀商品ID或数量为空，跳过数据库库存回滚, orderNo={}", message.getOrderNo());
			}

		} catch (Exception e) {
			log.error("处理退款成功消息失败, orderNo={}, userId={}",
					message.getOrderNo(), message.getUserId(), e);
			throw e; // 重新抛出，让MQ重试
		}
	}

	/**
	 * 监听支付成功死信队列 - 用于告警和人工干预
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_PAY_SUCCESS_DLX_QUEUE)
	public void listenPaySuccessDlxMessage(SeckillOrderPaySuccessMessage message) {
		log.error("【严重告警】支付成功消息进入死信队列，需要人工干预！消息内容：【{}】", message);

		// TODO: 这里可以添加告警通知
		// TODO: 也可以记录到专门的失败表中，供后续补偿处理

		// 注意：死信队列的消息不再抛出异常，避免无限循环
		// 应该通过监控告警，由人工或定时任务进行补偿处理
	}

	/**
	 * 监听订单取消死信队列 - 用于告警和人工干预
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_ORDER_CANCEL_DLX_QUEUE)
	public void listenOrderCancelDlxMessage(OrderTimeoutMessage message) {
		log.error("【严重告警】订单取消消息进入死信队列，需要人工干预！消息内容：【{}】", message);

		// TODO: 这里可以添加告警通知
		// TODO: 也可以记录到专门的失败表中，供后续补偿处理

		// 注意：死信队列的消息不再抛出异常，避免无限循环
		// 应该通过监控告警，由人工或定时任务进行补偿处理
	}

	/**
	 * 监听退款成功死信队列 - 用于告警和人工干预
	 */
	@RabbitListener(queues = RabbitConstants.SECKILL_REFUND_SUCCESS_DLX_QUEUE)
	public void listenRefundSuccessDlxMessage(SeckillOrderRefundSuccessMessage message) {
		log.error("【严重告警】退款成功消息进入死信队列，需要人工干预！消息内容：【{}】", message);

		// TODO: 这里可以添加告警通知
		// TODO: 也可以记录到专门的失败表中，供后续补偿处理

		// 注意：死信队列的消息不再抛出异常，避免无限循环
		// 应该通过监控告警，由人工或定时任务进行补偿处理
	}
}