package com.haalan.order.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderMessage;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.*;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.service.IMessagePushService;
import com.haalan.order.service.ITOrderItemService;
import com.haalan.order.service.ITSeckillOrder0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

	private final StringRedisTemplate stringRedisTemplate;
	private final IMessagePushService messagePushService;
	private final ITOrderItemService orderItemService;

	private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>();

	static {
		ROLLBACK_SCRIPT.setScriptSource(
				new ResourceScriptSource(new ClassPathResource("lua/rollback_seckill.lua"))
		);
		ROLLBACK_SCRIPT.setResultType(Long.class);
	}

	@Override
	public void saveMsg(SeckillOrderMessage message) {
		// 1. 获取SKU详细信息
		SkuDetailVO skuDetail = itemServiceClient.getSkuDetail(message.getSkuId());

		// 2. 保存秒杀订单
		TSeckillOrder seckillOrder = TSeckillOrder.builder()
				.orderNo(message.getPreOrderNo())
				.userId(message.getUserId())
				.activityId(message.getActivityId())
				.seckillProductId(message.getSeckillProductId())
				.skuId(message.getSkuId())
				.wechatProductCode(message.getWechatProductCode())
				.alipayProductCode(message.getAlipayProductCode())
				.productName(skuDetail.getSkuName())
				.seckillPrice(message.getSeckillPrice())
				.quantity(message.getQuantity())
				.totalAmount(message.getTotalAmount())
				.orderTime(message.getCreateTime())
				.status(0)
				.createTime(message.getCreateTime())
				.build();
		this.save(seckillOrder);

		// 3. 保存订单商品明细
		TOrderItem orderItem = new TOrderItem();
		orderItem.setOrderId(seckillOrder.getId());
		orderItem.setOrderNo(message.getPreOrderNo());
		orderItem.setSkuId(message.getSkuId());
		orderItem.setSkuCode(skuDetail.getSkuCode());
		orderItem.setProductName(skuDetail.getSkuName());
		orderItem.setProductImage(skuDetail.getImages());
		orderItem.setPrice(message.getSeckillPrice());
		orderItem.setQuantity(message.getQuantity());
		orderItem.setTotalPrice(message.getTotalAmount());
		// 将规格信息转换为JSON字符串存储
		if (skuDetail.getSpecifications() != null && !skuDetail.getSpecifications().isEmpty()) {
			orderItem.setSpecifications(JSONUtil.toJsonStr(skuDetail.getSpecifications()));
		}
		orderItem.setCreateTime(message.getCreateTime());
		orderItemService.save(orderItem);
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
				// 不存在重发, 等待下次重试 ,不回滚防止超卖
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
			order.setCancelTime(LocalDateTime.now());
			order.setCancelReason("订单超时取消");
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

	@Override
	public CancelOrderResponseVO cancelOrder(String orderNo, Long userId, String cancelReason) {
		// 设置userId到ThreadLocal，动态表名插件会自动选择正确的分表
		UserContext.setUser(userId);
		try {
			// 1. 查询订单
			TSeckillOrder order = this.getOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);

			if (order == null) {
				throw new BizIllegalException("订单不存在");
			}

			// 2. 验证用户权限
			if (!order.getUserId().equals(userId)) {
				throw new BizIllegalException("无权操作此订单");
			}

			// 3. 只有待支付的订单才能取消
			if (order.getStatus() != 0) {
				throw new BizIllegalException("订单状态异常，无法取消");
			}

			// 4. 更新订单状态为已取消（状态2）
			order.setStatus(2);
			order.setCancelReason(cancelReason);
			order.setCancelTime(LocalDateTime.now());
			boolean updated = this.updateById(order);

			if (!updated) {
				log.error("更新订单状态失败: {}", orderNo);
				throw new BizIllegalException("取消订单失败");
			}

			// 5. 回滚库存和购买记录
			OrderTimeoutMessage message = OrderTimeoutMessage.builder()
					.orderNo(orderNo)
					.userId(userId)
					.activityId(order.getActivityId())
					.seckillProductId(order.getSeckillProductId())
					.quantity(order.getQuantity())
					.build();

			boolean rollbackSuccess = rollbackStockAndBuyRecordWithRetry(message, 3);

			if (!rollbackSuccess) {
				log.error("库存回滚最终失败，需要人工处理: {}", orderNo);
				throw new BizIllegalException("库存回滚失败，请联系客服");
			}

			log.info("订单已取消: {}, 原因: {}, 库存已回滚", orderNo, cancelReason);

			// 6. 推送WebSocket消息通知客户端
			CancelOrderResponseVO response = CancelOrderResponseVO.builder()
					.orderNo(orderNo)
					.status(2)
					.statusName("已取消")
					.build();
			messagePushService.pushOrderCancel(userId, response);

			// 7. 返回响应
			return response;

		} finally {
			// 清理ThreadLocal，防止内存泄漏
			UserContext.removeUser();
		}
	}

	@Override
	public OrderDetailVO getOrderDetail(String orderNo, Long userId) {
		// 设置userId到ThreadLocal，动态表名插件会自动选择正确的分表
		UserContext.setUser(userId);
		try {
			// 1. 查询秒杀订单
			TSeckillOrder seckillOrder = this.getOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
							.eq(TSeckillOrder::getUserId, userId)
			);

			if (seckillOrder == null) {
				throw new BizIllegalException("订单不存在");
			}

			// 2. 构建订单详情VO（秒杀订单）
			OrderDetailVO orderDetailVO = new OrderDetailVO();
			orderDetailVO.setOrderId(seckillOrder.getId());
			orderDetailVO.setOrderNo(seckillOrder.getOrderNo());
			orderDetailVO.setUserId(seckillOrder.getUserId());
			orderDetailVO.setOrderType(2); // 2-秒杀订单
			orderDetailVO.setOrderTypeName("秒杀订单");
			// 秒杀订单不设置totalAmount、discountAmount、actualAmount
			orderDetailVO.setStatus(seckillOrder.getStatus());
			orderDetailVO.setStatusName(getStatusName(seckillOrder.getStatus()));
			orderDetailVO.setCreateTime(seckillOrder.getOrderTime());

			// 3. 计算支付过期时间（假设15分钟）
			LocalDateTime payExpireTime = seckillOrder.getOrderTime().plusMinutes(15);
			orderDetailVO.setPayExpireTime(payExpireTime);

			// 4. 计算剩余支付时间
			long remainingSeconds = 0;
			if (seckillOrder.getStatus() == 0) { // 待支付状态才计算
				remainingSeconds = java.time.Duration.between(LocalDateTime.now(), payExpireTime).getSeconds();
				if (remainingSeconds < 0) {
					remainingSeconds = 0;
				}
			}
			orderDetailVO.setRemainingSeconds(remainingSeconds);

			// 5. 查询订单商品明细
			List<TOrderItem> orderItems = orderItemService.lambdaQuery()
					.eq(TOrderItem::getOrderNo, orderNo)
					.list();

			List<OrderItemVO> orderItemVOList = orderItems.stream()
					.map(item -> {
						OrderItemVO itemVO = new OrderItemVO();
						itemVO.setSkuId(item.getSkuId());
						// 从SKU获取SPU ID
						SkuDetailVO skuDetail = itemServiceClient.getSkuDetail(item.getSkuId());
						itemVO.setSpuId(skuDetail != null ? skuDetail.getSpuId() : null);
						itemVO.setProductName(item.getProductName());
						itemVO.setProductImage(item.getProductImage());
						// 解析规格信息
						if (StrUtil.isNotBlank(item.getSpecifications())) {
							itemVO.setSpecifications(JSONUtil.toBean(item.getSpecifications(), Map.class));
						}
						itemVO.setPrice(item.getPrice());
						itemVO.setQuantity(item.getQuantity());
						itemVO.setTotalPrice(item.getTotalPrice());
						return itemVO;
					})
					.collect(Collectors.toList());

			orderDetailVO.setOrderItems(orderItemVOList);

			// 6. 收货地址信息（秒杀订单可能没有，返回空对象）
			AddressInfoVO addressInfo = new AddressInfoVO();
			addressInfo.setReceiverName("");
			addressInfo.setReceiverPhone("");
			addressInfo.setFullAddress("");
			orderDetailVO.setAddressInfo(addressInfo);

			return orderDetailVO;

		} finally {
			// 清理ThreadLocal，防止内存泄漏
			UserContext.removeUser();
		}
	}

	/**
	 * 获取订单状态名称
	 */
	private String getStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		switch (status) {
			case 0:
				return "待支付";
			case 1:
				return "已支付";
			case 2:
				return "已取消";
			case 3:
				return "已超时";
			default:
				return "未知";
		}
	}

	@Override
	public PageResult<OrderListItemVO> getSeckillOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status) {
		// 设置userId到ThreadLocal，动态表名插件会自动选择正确的分表
		UserContext.setUser(userId);
		try {
			// 1. 构建分页对象
			Page<TSeckillOrder> page = new Page<>(pageNum, pageSize);

			// 2. 构建查询条件
			LambdaQueryWrapper<TSeckillOrder> queryWrapper = new LambdaQueryWrapper<>();
			queryWrapper.eq(TSeckillOrder::getUserId, userId);
			if (status != null) {
				queryWrapper.eq(TSeckillOrder::getStatus, status);
			}
			queryWrapper.orderByDesc(TSeckillOrder::getCreateTime);

			// 3. 执行分页查询
			Page<TSeckillOrder> orderPage = this.page(page, queryWrapper);

			// 4. 转换为VO
			List<OrderListItemVO> voList = orderPage.getRecords().stream()
					.map(order -> {
						OrderListItemVO vo = new OrderListItemVO();
						vo.setOrderNo(order.getOrderNo());
						vo.setOrderType(2); // 秒杀订单
						vo.setOrderTypeName("秒杀订单");
						vo.setTotalAmount(order.getTotalAmount());
						// 秒杀订单不设置actualAmount
						vo.setStatus(order.getStatus());
						vo.setStatusName(getStatusName(order.getStatus()));
						vo.setProductName(order.getProductName());
						vo.setQuantity(order.getQuantity());
						vo.setCreateTime(order.getOrderTime());


						// 从订单商品明细中获取商品图片
						TOrderItem orderItem = orderItemService.lambdaQuery()
								.eq(TOrderItem::getOrderNo, order.getOrderNo())
								.last("LIMIT 1")
								.one();
						if (orderItem != null) {
							vo.setProductImage(orderItem.getProductImage());
						}

						return vo;
					})
					.collect(Collectors.toList());

			// 5. 构建分页结果
			PageResult<OrderListItemVO> result = new PageResult<>();
			result.setTotal(orderPage.getTotal());
			result.setPageNum((int) orderPage.getCurrent());
			result.setPageSize((int) orderPage.getSize());
			result.setList(voList);

			return result;

		} finally {
			// 清理ThreadLocal，防止内存泄漏
			UserContext.removeUser();
		}
	}
}