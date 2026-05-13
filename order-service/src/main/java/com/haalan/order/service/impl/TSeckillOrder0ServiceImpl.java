package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderMessage;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.service.ITSeckillOrder0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TSeckillOrder0ServiceImpl extends ServiceImpl<TSeckillOrder0Mapper, TSeckillOrder>
		implements ITSeckillOrder0Service {

	private final ItemServiceClient itemServiceClient;

	public static final String SECKILL_STOCK = "seckill:stock:";
	public static final String SECKILL_USER_BUY_PREFIX = "seckill:buy:";
	public static final String SECKILL_USER_LIMIT_PREFIX = "seckill:limit:";
	public static final String SECKILL_ACTIVITY_LIMIT_PREFIX = "seckill:limit:activity:";

	private final RedisTemplate<String, Object> redisTemplate;
	private final StringRedisTemplate stringRedisTemplate;

	private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>();

	static {
		ROLLBACK_SCRIPT.setScriptSource(
				new ResourceScriptSource(new ClassPathResource("lua/rollback_seckill.lua"))
		);
		ROLLBACK_SCRIPT.setResultType(Long.class);
	}

	@Override
	public void saveMsg(SeckillOrderMessage message) {
		TSeckillOrder seckillOrder = TSeckillOrder.builder()
				.orderNo(message.getPreOrderNo())
				.userId(message.getUserId())
				.activityId(message.getActivityId())
				.seckillProductId(message.getSeckillProductId())
				.skuId(message.getSkuId())
				.wechatProductCode(message.getWechatProductCode())
				.alipayProductCode(message.getAlipayProductCode())
				.productName(itemServiceClient.getCode(message.getSkuId()).getProductName())
				.seckillPrice(message.getSeckillPrice())
				.quantity(message.getQuantity())
				.totalAmount(message.getTotalAmount())
				.status(0)
				.createTime(message.getCreateTime())
				.build();
		this.save(seckillOrder);
	}

	@Override
	public void setStatus(OrderTimeoutMessage message) {
		// 设置userId到ThreadLocal，动态表名插件会自动选择正确的分表
		UserContext.setUser(message.getUserId());
		try {
			// 1. 查询订单
			TSeckillOrder order = this.getOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, message.getOrderNo())
			);

			if (order == null) {
				log.warn("订单不存在，稍后重试: {}", message.getOrderNo());
				throw new RuntimeException("订单不存在: " + message.getOrderNo());
			}

			// 2. 只有待支付的订单才取消
			if (order.getStatus() != 0) {
				log.info("订单状态不是待支付，跳过: {}, 当前状态: {}",
						message.getOrderNo(), order.getStatus());
				return;
			}

			// 3. 更新订单状态为已取消
			order.setStatus(3);
			boolean updated = this.updateById(order);

			if (!updated) {
				log.error("更新订单状态失败: {}", message.getOrderNo());
				throw new RuntimeException("更新订单状态失败: " + message.getOrderNo());
			}

			// 4. Lua 脚本回滚（增加重试机制）
			boolean rollbackSuccess = rollbackStockAndBuyRecordWithRetry(message, 3);

			if (!rollbackSuccess) {
				log.error("库存回滚最终失败，需要人工处理: {}", message.getOrderNo());
				// 可以发送告警或记录到失败表
				throw new RuntimeException("库存回滚失败: " + message.getOrderNo());
			}

			log.info("订单已超时取消: {}, 库存已回滚", message.getOrderNo());
		} finally {
			// 清理ThreadLocal，防止内存泄漏
			UserContext.removeUser();
		}
	}

	/**
	 * Lua 脚本原子操作：回滚库存 + 购买记录（带重试）
	 */
	private boolean rollbackStockAndBuyRecordWithRetry(OrderTimeoutMessage message, int maxRetries) {
		for (int i = 0; i < maxRetries; i++) {
			try {
				rollbackStockAndBuyRecord(message);
				return true;
			} catch (Exception e) {
				log.warn("回滚失败，重试 {}/{}, orderNo={}, error={}",
						i + 1, maxRetries, message.getOrderNo(), e.getMessage());
				if (i == maxRetries - 1) {
					log.error("回滚最终失败，orderNo={}", message.getOrderNo(), e);
					return false;
				}
				try {
					Thread.sleep(100 * (i + 1)); // 递增延迟
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
		return false;
	}

	/**
	 * Lua 脚本原子操作：回滚库存 + 购买记录
	 */
	private void rollbackStockAndBuyRecord(OrderTimeoutMessage message) {
		String stockKey = SECKILL_STOCK + message.getActivityId()
				+ ":" + message.getSeckillProductId();
		String buyKey = SECKILL_USER_BUY_PREFIX + message.getUserId();
		String productLimitKey = SECKILL_USER_LIMIT_PREFIX + message.getUserId()
				+ ":" + message.getSeckillProductId();
		String activityLimitKey = SECKILL_ACTIVITY_LIMIT_PREFIX + message.getActivityId();

		List<String> keys = Arrays.asList(
				stockKey,
				buyKey,
				activityLimitKey,  // KEYS[3]: 活动限购key
				productLimitKey    // KEYS[4]: 商品限购key
		);

		Object[] args = {
				String.valueOf(message.getQuantity()),
				String.valueOf(message.getSeckillProductId()),
				String.valueOf(message.getActivityId())
		};

		log.info("执行回滚Lua脚本, keys={}, args={}, orderNo={}",
				keys, args, message.getOrderNo());

		// 使用 StringRedisTemplate 执行 Lua 脚本，避免 JSON 序列化问题
		Long result = stringRedisTemplate.execute(ROLLBACK_SCRIPT, keys, args);

		if (result == null || result != 0) {
			log.error("回滚脚本执行失败, result={}, orderNo={}", result, message.getOrderNo());
			throw new RuntimeException("回滚脚本执行失败，返回码: " + result);
		}

		log.info("回滚成功, orderNo={}, userId={}, productId={}, quantity={}",
				message.getOrderNo(), message.getUserId(),
				message.getSeckillProductId(), message.getQuantity());
	}

	@Override
	public TSeckillOrder getAllByNo(String orderNo, Long userId) {
		// 设置userId到ThreadLocal，动态表名插件会自动选择正确的分表
		UserContext.setUser(userId);
		try {
			return this.getOne(new LambdaQueryWrapper<TSeckillOrder>()
					.eq(TSeckillOrder::getOrderNo, orderNo));
		} finally {
			// 清理ThreadLocal，防止内存泄漏
			UserContext.removeUser();
		}
	}
}