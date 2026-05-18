package com.haalan.seckill.service.impl;

import cn.hutool.core.lang.UUID;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.seckill.config.STokenProperties;
import com.haalan.seckill.config.SeckillConstants;
import com.haalan.seckill.domain.dto.SeckillTokenRequestDTO;
import com.haalan.seckill.domain.vo.SeckillTokenVO;
import com.haalan.seckill.service.ITSeckillTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TSeckillTokenServiceImpl implements ITSeckillTokenService {
	@Resource
	private STokenProperties sTokenProperties;

	private final StringRedisTemplate redisTemplate;

	@Override
	public SeckillTokenVO generateToken(Long userId, SeckillTokenRequestDTO requestDTO) {
		Long activityId = requestDTO.getActivityId();
		Long seckillProductId = requestDTO.getSeckillProductId();

		log.info("开始生成秒杀令牌, userId={}, activityId={}, seckillProductId={}",
				userId, activityId, seckillProductId);

		// ============ 1. 快速校验（只做最必要的） ============
		// 活动状态校验
		validateActivityQuick(activityId);
		// 库存快速检查
		checkStockExists(activityId, seckillProductId);

		// ============ 2. 生成一次性令牌 ============
		String token = generateUniqueToken();

		// ============ 3. 保存令牌（极简String结构） ============
		saveTokenToRedis(token, userId, activityId, seckillProductId);

		log.info("一次性秒杀令牌生成成功, userId={}, activityId={}, seckillProductId={}, token={}",
				userId, activityId, seckillProductId, token);

		return SeckillTokenVO.builder()
				.seckillToken(token)
				.expireTime(sTokenProperties.getTimeOut())
				.seckillProductId(seckillProductId)
				.build();
	}

	/**
	 * 快速校验活动状态（只检查是否存在和状态）
	 */
	private void validateActivityQuick(Long activityId) {

		Object status = redisTemplate.opsForHash().get(SeckillConstants.SECKILL_ACTIVITY_LIST + activityId, "status");

		if (status == null) {
			throw new BizIllegalException("活动不存在");
		}

		if (!"1".equals(status)) {
			throw new BizIllegalException("活动不在进行中");
		}
	}

	/**
	 * 快速检查库存是否存在（宽松检查，不拦截）
	 */
	private void checkStockExists(Long activityId, Long seckillProductId) {
		String stockKey = SeckillConstants.SECKILL_STOCK_PREFIX + activityId + ":" + seckillProductId;
		String stockStr = redisTemplate.opsForValue().get(stockKey);

		if (stockStr == null) {
			log.warn("商品库存信息缺失, seckillProductId={}", seckillProductId);
			throw new BizIllegalException("商品不存在");
		}

		// 注意：这里不检查库存是否>0，令牌生成时可能有库存，使用时再检查
	}

	/**
	 * 生成唯一令牌
	 */
	private String generateUniqueToken() {
		String uuid = UUID.fastUUID().toString(true);
		return SeckillConstants.TOKEN_PREFIX + uuid;
	}

	/**
	 * 保存令牌到Redis（极简设计）
	 * Key: seckill:token:{token}
	 * Value: {userId}:{activityId}:{seckillProductId}
	 * TTL: 配置文件中的超时时间
	 */
	private void saveTokenToRedis(String token, Long userId,
								  Long activityId, Long seckillProductId) {
		String tokenKey = SeckillConstants.SECKILL_TOKEN_PREFIX + token;

		// ⭐ 核心：用最简单的格式存储令牌信息
		String tokenValue = String.format("%d:%d:%d", userId, activityId, seckillProductId);

		redisTemplate.opsForValue().set(
				tokenKey,
				tokenValue,
				sTokenProperties.getTimeOut(),
				TimeUnit.MILLISECONDS
		);

		log.info("令牌已存储到Redis, token={}, value={}, ttl={}s",
				token, tokenValue, sTokenProperties.getTimeOut());
	}
}