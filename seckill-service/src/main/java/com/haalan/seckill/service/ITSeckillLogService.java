package com.haalan.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.seckill.domain.po.TSeckillLog;

/**
 * <p>
 * 秒杀日志表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-12
 */
public interface ITSeckillLogService extends IService<TSeckillLog> {


	/**
	 * 记录用户秒杀行为日志到MQ
	 *
	 * @param userId     用户ID
	 * @param activityId 活动ID
	 * @param skuId      SKU ID
	 * @param action     操作类型: REQUEST/LOCK/SUCCESS/FAIL
	 * @param failReason 失败原因
	 * @param costTime   耗时(ms)
	 */
	void logToMq(Long userId, Long activityId, Long skuId, Long productId,
				 String action, String failReason, Integer costTime, String ip, String userAgent);
}
