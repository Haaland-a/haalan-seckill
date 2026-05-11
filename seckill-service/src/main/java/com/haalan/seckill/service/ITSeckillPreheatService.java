package com.haalan.seckill.service;

import com.haalan.seckill.domain.vo.SeckillActivityPreheatVO;

public interface ITSeckillPreheatService {

	/**
	 * 活动预热：双层缓存（Redis + 本地 JVM）+ 状态就绪标记
	 *
	 * @param activityId 活动ID
	 * @param force      是否强制重新预热（忽略已预热标记）
	 * @return 预热结果
	 */
	SeckillActivityPreheatVO preheatActivity(Long activityId, boolean force);
}