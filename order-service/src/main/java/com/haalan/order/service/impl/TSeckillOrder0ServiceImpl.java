package com.haalan.order.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.client.UserServiceClient;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.api.domain.vo.UserAddressVO;
import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderMessage;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.utils.ImageUtils;
import com.haalan.common.utils.UserContext;
import com.haalan.order.config.RabbitConstants;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.*;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.mapper.TSeckillOrder1Mapper;
import com.haalan.order.service.IMessagePushService;
import com.haalan.order.service.ITOrderItemService;
import com.haalan.order.service.ITSeckillOrder0Service;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final UserServiceClient userServiceClient;
	private final TSeckillOrder1Mapper seckillOrder1Mapper;

	public static final String SECKILL_STOCK = "seckill:stock:";
	public static final String SECKILL_USER_BUY_PREFIX = "seckill:buy:";
	public static final String SECKILL_USER_LIMIT_PREFIX = "seckill:limit:";
	public static final String SECKILL_ACTIVITY_LIMIT_PREFIX = "seckill:limit:activity:";

	private final StringRedisTemplate stringRedisTemplate;
	private final IMessagePushService messagePushService;
	private final ITOrderItemService orderItemService;
	private final RabbitTemplate rabbitTemplate;

	private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>();

	static {
		ROLLBACK_SCRIPT.setScriptSource(
				new ResourceScriptSource(new ClassPathResource("lua/rollback_seckill.lua"))
		);
		ROLLBACK_SCRIPT.setResultType(Long.class);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveMsg(SeckillOrderMessage message) {
		// 幂等：MQ 可能重投（ACK 超时、消费者重启）。
		// 原来没有这道判断，同一条消息消费两次就会插两条同 order_no 的订单，
		// 订单列表出现重复行、明细也翻倍。
		Long existed = this.lambdaQuery()
				.eq(TSeckillOrder::getOrderNo, message.getPreOrderNo())
				.count();
		if (existed != null && existed > 0) {
			log.info("秒杀订单已存在，跳过重复消费: orderNo={}", message.getPreOrderNo());
			return;
		}

		// 1. 获取SKU详细信息
		SkuDetailVO skuDetail = itemServiceClient.getSkuDetail(message.getSkuId());
		log.info("获取SKU详细信息: {}", skuDetail);
		// 2. 保存秒杀订单
		// 解密地址ID（从加密字符串转为Long）
		Long decryptedAddressId = message.getAddressId();


		TSeckillOrder seckillOrder = TSeckillOrder.builder()
				.orderNo(message.getPreOrderNo())
				.userId(message.getUserId())
				.activityId(message.getActivityId())
				.seckillProductId(message.getSeckillProductId())
				.skuId(message.getSkuId())
				.alipayProductCode(message.getAlipayProductCode())
				.productName(skuDetail.getSkuName())
				.seckillPrice(message.getSeckillPrice())
				.quantity(message.getQuantity())
				.totalAmount(message.getTotalAmount())
				.addressId(decryptedAddressId)
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
		orderItem.setProductImage(ImageUtils.extractFirstImage(skuDetail.getImages()));
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
	@GlobalTransactional(rollbackFor = Exception.class)
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
	@GlobalTransactional
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

			// 7. 发送MQ消息通知秒杀服务更新秒杀记录状态
			sendOrderCancelMessage(order, userId);

			// 8. 返回响应
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
			orderDetailVO.setTotalAmount(seckillOrder.getTotalAmount());
			// 秒杀订单不设置discountAmount、actualAmount
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

			// 6. 收货地址信息
			if (seckillOrder.getAddressId() != null) {
				try {
					log.info("开始调用用户服务获取地址, userId: {}, addressId: {}", userId, seckillOrder.getAddressId());
					UserAddressVO userAddress = userServiceClient.getUserAddressById(seckillOrder.getAddressId(), userId);
					log.info("用户服务返回结果: {}", userAddress);
					if (userAddress != null) {
						log.info("地址详情 - 姓名: {}, 电话: {}, 完整地址: {}",
								userAddress.getReceiverName(),
								userAddress.getReceiverPhone(),
								userAddress.getFullAddress());
						AddressInfoVO addressInfo = new AddressInfoVO();
						addressInfo.setReceiverName(userAddress.getReceiverName());
						addressInfo.setReceiverPhone(userAddress.getReceiverPhone());
						addressInfo.setFullAddress(userAddress.getFullAddress());
						addressInfo.setProvince(userAddress.getProvince());
						addressInfo.setCity(userAddress.getCity());
						addressInfo.setDistrict(userAddress.getDistrict());
						addressInfo.setDetailAddress(userAddress.getDetailAddress());
						orderDetailVO.setAddressInfo(addressInfo);
					} else {
						log.warn("用户服务返回的地址为null");
					}
				} catch (Exception e) {
					log.error("获取用户地址失败, addressId: {}", seckillOrder.getAddressId(), e);
					// 如果获取失败，返回空地址对象
					AddressInfoVO addressInfo = new AddressInfoVO();
					orderDetailVO.setAddressInfo(addressInfo);
				}
			} else {
				log.info("订单中没有地址ID");
				AddressInfoVO addressInfo = new AddressInfoVO();
				orderDetailVO.setAddressInfo(addressInfo);
			}

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

	/**
	 * 发送订单取消MQ消息，通知秒杀服务更新秒杀记录状态
	 *
	 * @param order  订单信息
	 * @param userId 用户ID
	 */
	private void sendOrderCancelMessage(TSeckillOrder order, Long userId) {
		try {
			OrderTimeoutMessage message = OrderTimeoutMessage.builder()
					.orderNo(order.getOrderNo())
					.userId(userId)
					.build();

			rabbitTemplate.convertAndSend(
					RabbitConstants.SECKILL_ORDER_CANCEL_EXCHANGE,
					RabbitConstants.SECKILL_ORDER_CANCEL_ROUTING_KEY,
					message
			);

			log.info("订单取消MQ消息已发送, orderNo={}, userId={}", order.getOrderNo(), userId);

		} catch (Exception e) {
			// MQ消息发送失败不影响主流程，记录日志即可
			log.error("发送订单取消MQ消息失败, orderNo={}, userId={}", order.getOrderNo(), userId, e);
		}
	}

	@Override
	public PageResult<OrderListItemVO> getSeckillOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status) {
		// 保留 ThreadLocal：同类的 getAllByNo / cancelOrder / getOrderDetail 仍依赖动态表名插件，
		// 它们走 getOne/updateById（beforeQuery/beforePrepare），插件生效。
		UserContext.setUser(userId);
		try {
			// 1. 按 userId % 2 手工选择分表 mapper。
			//    不能直接用 this.page(...)：MybatisPlusConfig 先注册 PaginationInnerInterceptor、
			//    后注册 DynamicTableNameInnerInterceptor，而 count 查询是 PaginationInnerInterceptor
			//    内部用 executor.query(...) 直接发起的（不会再次进入插件链），
			//    所以 DynamicTableNameInnerInterceptor.beforeQuery 永远拿不到 count 的 BoundSql，
			//    count SQL 里的表名仍是基表 t_seckill_order → 报
			//    Table 'order-db.t_seckill_order' doesn't exist。
			//    TPaymentServiceImpl.selectOrder / TRefundServiceImpl 用的就是这个姿势。
			int tableSuffix = (int) (userId % 2);
			BaseMapper<TSeckillOrder> seckillOrderMapper =
					tableSuffix == 0 ? getBaseMapper() : seckillOrder1Mapper;

			// 2. 构建分页对象：先关掉 searchCount，避免 selectPage 内部自动发起 count 查询。
			//    原因见下方「count 查询为什么必须绕开」注释。
			Page<TSeckillOrder> page = new Page<>(pageNum, pageSize);
			page.setSearchCount(false);

			// 3. 构建查询条件
			LambdaQueryWrapper<TSeckillOrder> queryWrapper = new LambdaQueryWrapper<>();
			queryWrapper.eq(TSeckillOrder::getUserId, userId);
			if (status != null) {
				queryWrapper.eq(TSeckillOrder::getStatus, status);
			}
			queryWrapper.orderByDesc(TSeckillOrder::getCreateTime);

			// 4. 先单独查总数，再执行数据查询。
			//    count 查询为什么必须绕开：
			//    MybatisPlusConfig 先注册 PaginationInnerInterceptor、后注册 DynamicTableNameInnerInterceptor。
			//    PaginationInnerInterceptor.willDoQuery 在 page.searchCount() 为 true 时，会自己
			//    buildAutoCountMappedStatement + autoCountSql 拼出 SELECT COUNT(*) ... 然后用
			//    executor.query(...) 直接执行；MyBatis 的插件代理是按方法级别触发的，这次内部调用
			//    不会再进入 MybatisPlusInterceptor.intercept，所以链上后注册的
			//    DynamicTableNameInnerInterceptor.beforeQuery 拿不到这次调用的 BoundSql，
			//    count SQL 的表名就停留在基表 t_seckill_order → 报
			//    Table 'order-db.t_seckill_order' doesn't exist。
			//    selectCount 不带 page 参数，PaginationInnerInterceptor.willDoQuery 直接 return true
			//    不执行上面的 count 分支，于是会正常走到 DynamicTableNameInnerInterceptor.beforeQuery，
			//    由它把表名替换成 t_seckill_order_0 / t_seckill_order_1。
			Long total = SqlHelper.retCount(seckillOrderMapper.selectCount(queryWrapper));
			page.setTotal(total == null ? 0L : total);

			// 5. 执行分页查询（searchCount 已关，不会再走上面的 count 分支）
			Page<TSeckillOrder> orderPage = seckillOrderMapper.selectPage(page, queryWrapper);

			// 6. 转换为VO
			List<OrderListItemVO> voList = orderPage.getRecords().stream()					.map(order -> {
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
						vo.setUpdateTime(order.getUpdateTime());


						// 从订单商品明细中获取商品图片
						// 与 TOrderServiceImpl 保持一致：一个订单可能有多条明细，
						// 这里用 one() 时 MyBatis-Plus 的 selectOne 查出多条会抛
						// TooManyResultsException: Expected one result (or null) to be returned by selectOne()
						// 导致整个订单列表接口 500，所以统一改为取首条明细。
						List<TOrderItem> seckillOrderItems = orderItemService.lambdaQuery()
								.eq(TOrderItem::getOrderNo, order.getOrderNo())
								.list();
						if (seckillOrderItems != null && !seckillOrderItems.isEmpty()) {
							TOrderItem orderItem = seckillOrderItems.get(0);
							vo.setProductImage(orderItem.getProductImage());
						}

						return vo;
					})
					.collect(Collectors.toList());

			// 7. 构建分页结果
			PageResult<OrderListItemVO> result = new PageResult<>();
			// total 已在第 4 步由 selectCount 查得并 setTotal，这里直接取；
			// searchCount=false 时 selectPage 不会覆盖它。
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