package com.haalan.seckill.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 JVM 缓存 —— 只做快速失败，不做库存扣减
 * <p>
 * 职责：
 * 1. 售罄标记（活动期间某个商品卖光了，直接拦截，不请求 Redis）
 * 2. 预热到本地时记录初始库存（用于监控统计，不参与扣减逻辑）
 * <p>
 * 真正的库存扣减全部走 Redis Lua 脚本
 */
@Slf4j
@Component
public class LocalSeckillCache {

	// 售罄标记（商品 ID）
	private final Set<Long> soldOutSet = ConcurrentHashMap.newKeySet();

	/**
	 * 预热时初始化售罄状态（库存为 0 的直接标记售罄）
	 */
	public void preheatSoldOutStatus(Long productId, int stock) {
		if (stock <= 0) {
			soldOutSet.add(productId);
		} else {
			soldOutSet.remove(productId);
		}
	}

	/**
	 * 标记售罄（Redis 扣减到 0 时回调）
	 */
	public void markSoldOut(Long productId) {
		soldOutSet.add(productId);
	}

	/**
	 * 是否已售罄（本地快速判断，无需访问 Redis）
	 */
	public boolean isSoldOut(Long productId) {
		return soldOutSet.contains(productId);
	}

	/**
	 * 清理活动本地状态
	 */
	public void clearActivity(Set<Long> productIds) {
		productIds.forEach(soldOutSet::remove);
	}
}