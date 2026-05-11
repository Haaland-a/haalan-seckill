package com.haalan.seckill.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.seckill.config.RabbitConstants;
import com.haalan.seckill.config.SeckillConstants;
import com.haalan.seckill.domain.dto.SeckillExecuteRequestDTO;
import com.haalan.seckill.domain.po.OrderTimeoutMessage;
import com.haalan.seckill.domain.po.SeckillOrderMessage;
import com.haalan.seckill.domain.vo.SeckillExecuteResultVO;
import com.haalan.seckill.domain.vo.SeckillOrderVO;
import com.haalan.seckill.domain.vo.SeckillQueueVO;
import com.haalan.seckill.service.ISeckillExecuteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillExecuteServiceImpl implements ISeckillExecuteService {

	private final StringRedisTemplate redisTemplate;

	private final RabbitTemplate rabbitTemplate;

	private static final DefaultRedisScript<Long> SECKILL_EXECUTE_SCRIPT = new DefaultRedisScript<>();

	static {
		SECKILL_EXECUTE_SCRIPT.setScriptSource(
				new ResourceScriptSource(new ClassPathResource("lua/tempfile.lua"))
		);
		SECKILL_EXECUTE_SCRIPT.setResultType(Long.class);
	}


	@Override
	public SeckillExecuteResultVO execute(Long userId, SeckillExecuteRequestDTO request) {
		String requestId = request.getRequestId();
		Long activityId = request.getActivityId();
		Long seckillProductId = request.getSeckillProductId();
		Integer quantity = request.getQuantity();

		log.info("开始执行秒杀, userId={}, requestId={}, activityId={}, seckillProductId={}",
				userId, requestId, activityId, seckillProductId);

		// ============ 第一层：幂等性控制(提前占位) ============
		String idempotentKey = SeckillConstants.SECKILL_IDEMPOTENT_PREFIX + requestId;

		// 使用 SETNX 原子性地设置占位符，防止并发重复处理
		Boolean isFirstRequest = redisTemplate.opsForValue()
				.setIfAbsent(idempotentKey, "PROCESSING",
						SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS, TimeUnit.SECONDS);

		if (Boolean.FALSE.equals(isFirstRequest)) {
			// 已有请求在处理，检查最终结果
			String existingResult = redisTemplate.opsForValue().get(idempotentKey);
			if ("PROCESSING".equals(existingResult)) {
				// 前一个请求还在处理中，返回排队状态
				log.info("请求正在处理中，返回排队状态, requestId={}", requestId);
				return buildQueueResult(requestId);
			} else {
				// 已处理完成，尝试获取完整订单信息
				log.info("重复请求，获取已有结果, requestId={}, value={}", requestId, existingResult);
				return getExistingResult(requestId, existingResult);
			}
		}

		try {
			// ============ 第二层：令牌验证 ============
			validateSeckillToken(userId, request.getSeckillToken(), activityId, seckillProductId);

			// ============ 第三层：验证活动信息 ============
			Map<String, String> activity = getActivityFromRedis(activityId);

			// ============ 第四层：验证商品信息 ============
			Map<String, String> product = getProductFromRedis(activityId, seckillProductId);

			// ============ 第五层：验证活动状态 ============
			validateActivity(activityId, activity);

			// ============ 第六层：验证商品状态 ============
			validateProduct(activityId, seckillProductId);

			// ============ 第七层：验证用户购买限制 ============
			checkUserPurchaseLimit(userId, activityId, seckillProductId, quantity);

			// ============ 第八层：原子性库存扣减 ============
			Long result = executeStockDecrement(activityId, seckillProductId, userId, quantity);

			if (result == -1) {
				setIdempotentResult(idempotentKey, "FAILED:库存不足");
				throw new BizIllegalException("库存不足");
			} else if (result == -2) {
				setIdempotentResult(idempotentKey, "FAILED:商品限购");
				throw new BizIllegalException("您已达到该商品的购买上限");
			} else if (result == -3) {
				setIdempotentResult(idempotentKey, "FAILED:活动限购");
				throw new BizIllegalException("您已达到该活动的购买上限");
			}

			// ============ 第九层：创建预订单 ============
			String preOrderNo = generateOrderNo();
			BigDecimal totalAmount = calculateTotalAmount(product, quantity);

			// 创建预订单并缓存
			SeckillOrderVO orderVO = createPreOrder(userId, request, activity, product,
					preOrderNo, totalAmount);

			// ============ 第十层：可靠消息投递 ============
			String messageId = UUID.fastUUID().toString(true);
			SeckillOrderMessage message = buildOrderMessage(messageId, preOrderNo, userId, product,
					request, totalAmount);

			// 关键步骤：先持久化消息记录到Redis，再发送MQ
			boolean messageSent = sendMessageReliably(message, preOrderNo);

			if (!messageSent) {
				// 消息发送失败，但已记录在Redis中，由定时任务补偿
				log.error("MQ消息发送失败，已记录待补偿, messageId={}, orderNo={}",
						messageId, preOrderNo);
				// 此时仍返回成功，因为库存已扣减，订单已生成
			}
			// ============ 第十一层：发送订单超时延迟队列 ============
			boolean timeMessageSent = sendTimeoutMessage(message);
			if (!timeMessageSent) {
				// 消息发送失败，但已记录在Redis中，由定时任务补偿
				log.error("MQ消息发送失败，此订单无法mq异步定义超时时间 {}", messageId);
			}
			// ============ 更新幂等状态 ============
			setIdempotentResult(idempotentKey, "SUCCESS:" + preOrderNo);

			log.info("秒杀成功, userId={}, orderNo={}, messageId={}", userId, preOrderNo, messageId);

			return SeckillExecuteResultVO.builder()
					.success(true)
					.orderVO(orderVO)
					.build();

		} catch (BizIllegalException e) {
			// 业务异常，更新幂等状态
			String failReason = e.getMessage() != null ? e.getMessage() : "未知错误";
			setIdempotentResult(idempotentKey, "FAILED:" + failReason);
			throw e;
		} catch (Exception e) {
			// 未知异常，更新幂等状态
			log.error("秒杀执行异常", e);
			setIdempotentResult(idempotentKey, "ERROR:系统异常");
			throw new BizIllegalException("秒杀失败，请稍后重试");
		}
	}

	/**
	 * 可靠消息发送（核心方法）
	 * 1. 先在Redis记录待确认消息
	 * 2. 再用RabbitMQ确认机制发送
	 * 3. 发送成功后更新消息状态
	 */
	private boolean sendMessageReliably(SeckillOrderMessage message,
										String orderNo) {
		try {
			// 步骤1: 在Redis中记录待确认消息（持久化）
			String pendingKey = RabbitConstants.SECKILL_PENDING_MSG_PREFIX + message.getMessageId();
			String messageJson = JSONUtil.toJsonStr(message);

			redisTemplate.opsForValue().set(
					pendingKey,
					messageJson,
					RabbitConstants.PENDING_MSG_EXPIRE_MINUTES,
					TimeUnit.MINUTES
			);

			log.info("待确认消息已记录到Redis, messageId={}, orderNo={}",
					message.getMessageId(), orderNo);

			// 步骤2: 发送MQ消息（带确认）
			CorrelationData correlationData = new CorrelationData(message.getMessageId());
			//发送成功删除待确认消息
			correlationData.getFuture().addCallback(
					confirm -> {
						if (confirm.isAck()) {
							String pendingMsgKey = RabbitConstants.SECKILL_PENDING_MSG_PREFIX
									+ correlationData.getId();
							redisTemplate.delete(pendingMsgKey);
							log.info("MQ消息已确认，删除待确认记录, messageId={}", correlationData.getId());
						} else {
							log.error("MQ消息发送失败, messageId={}, reason={}",
									correlationData.getId(), confirm.getReason());
						}
					},
					ex -> {
						log.error("MQ消息确认异常, messageId={}", correlationData.getId(), ex);
					}
			);
			// 发送消息
			rabbitTemplate.convertAndSend(
					RabbitConstants.SECKILL_ORDER_EXCHANGE,  // 交换机
					RabbitConstants.SECKILL_ORDER_ROUTING_KEY,  // 路由键
					message,
					correlationData
			);

			log.info("MQ消息已投递, messageId={}, orderNo={}", message.getMessageId(), orderNo);
			return true;

		} catch (Exception e) {
			log.error("发送可靠消息异常, messageId={}, orderNo={}",
					message.getMessageId(), orderNo, e);
			// 消息已记录在Redis中，由定时任务补偿
			return false;
		}
	}

	/**
	 * 发送订单超时消息到延时队列
	 */
	private Boolean sendTimeoutMessage(SeckillOrderMessage Message) {
		OrderTimeoutMessage timeoutMsg = new OrderTimeoutMessage();
		timeoutMsg.setOrderNo(Message.getPreOrderNo());
		timeoutMsg.setActivityId(Message.getActivityId());
		timeoutMsg.setSeckillProductId(Message.getSeckillProductId());
		timeoutMsg.setSkuId(Message.getSkuId());
		timeoutMsg.setUserId(Message.getUserId());
		timeoutMsg.setQuantity(Message.getQuantity());

		// 发送到延时队列（没有交换机，直接发队列）
		// 使用默认回调函数
		try {
			rabbitTemplate.convertAndSend(RabbitConstants.ORDER_TIMEOUT_QUEUE, timeoutMsg);
			log.info("订单超时消息已发送: {}", Message.getPreOrderNo());
			return true;
		} catch (AmqpException e) {
			log.error("发送订单超时消息异常: {}", Message.getPreOrderNo(), e);
			return false;
		}
	}

	/**
	 * 构建订单消息
	 */
	private SeckillOrderMessage buildOrderMessage(String messageId, String preOrderNo,
												  Long userId,
												  Map<String, String> product,
												  SeckillExecuteRequestDTO request,
												  BigDecimal totalAmount) {
		return SeckillOrderMessage.builder()
				.messageId(messageId)
				.preOrderNo(preOrderNo)
				.userId(userId)
				.requestId(request.getRequestId())
				.activityId(request.getActivityId())
				.skuId(request.getSkuId())
				.seckillProductId(request.getSeckillProductId())
				.seckillPrice(
						new BigDecimal(product.get("seckillPrice"))
				)
				.quantity(request.getQuantity())
				.totalAmount(totalAmount)
				.createTime(LocalDateTime.now())
				.build();
	}

	/**
	 * 创建预订单并缓存
	 */
	private SeckillOrderVO createPreOrder(Long userId, SeckillExecuteRequestDTO request,
										  Map<String, String> activity,
										  Map<String, String> product,
										  String orderNo, BigDecimal totalAmount) {
		BigDecimal seckillPrice = new BigDecimal(product.get("seckillPrice"));
		LocalDateTime payExpireTime = LocalDateTime.now()
				.plusMinutes(SeckillConstants.ORDER_PAY_TIMEOUT_MINUTES);

		SeckillOrderVO orderVO = SeckillOrderVO.builder()
				.orderNo(orderNo)
				.seckillProductId(request.getSeckillProductId())
				.productName(product.get("name"))
				.seckillPrice(seckillPrice)
				.quantity(request.getQuantity())
				.totalAmount(totalAmount)
				.payExpireTime(payExpireTime)
				.status(0)
				.statusName("待支付")
				.build();

		// 缓存预订单信息（用于轮询查询）
		String orderCacheKey = SeckillConstants.SECKILL_ORDER_PREFIX + orderNo;
		try {
			String orderJson = JSONUtil.toJsonStr(orderVO);
			redisTemplate.opsForValue().set(
					orderCacheKey,
					orderJson,
					SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS,
					TimeUnit.SECONDS
			);
		} catch (Exception e) {
			log.error("缓存预订单失败, orderNo={}", orderNo, e);
		}

		// 设置订单超时（用于超时取消）
		redisTemplate.opsForValue().set(
				SeckillConstants.SECKILL_ORDER_TIMEOUT_PREFIX + orderNo,
				String.valueOf(userId),
				SeckillConstants.ORDER_PAY_TIMEOUT_MINUTES,
				TimeUnit.MINUTES
		);

		log.info("预订单创建成功, userId={}, orderNo={}", userId, orderNo);
		return orderVO;
	}

	/**
	 * 计算总金额
	 */
	private BigDecimal calculateTotalAmount(Map<String, String> product, Integer quantity) {
		BigDecimal seckillPrice = new BigDecimal(product.get("seckillPrice"));
		return seckillPrice.multiply(new BigDecimal(quantity));
	}

	/**
	 * 构建排队结果
	 */
	private SeckillExecuteResultVO buildQueueResult(String requestId) {
		return SeckillExecuteResultVO.builder()
				.success(false)
				.queueVO(SeckillQueueVO.builder()
						.requestId(requestId)
						.queuePosition(0)  // 无法准确计算
						.estimatedTime(3)   // 建议3秒后重试
						.build())
				.build();
	}

	/**
	 * 获取已存在的处理结果
	 */
	private SeckillExecuteResultVO getExistingResult(String requestId, String resultValue) {
		if (resultValue == null) {
			return buildQueueResult(requestId);
		}

		if (resultValue.startsWith("SUCCESS:")) {
			String orderNo = resultValue.substring("SUCCESS:".length());
			// 尝试从缓存获取完整订单信息
			String orderCacheKey = SeckillConstants.SECKILL_ORDER_PREFIX + orderNo;
			String orderJson = redisTemplate.opsForValue().get(orderCacheKey);

			if (orderJson != null) {
				try {
					SeckillOrderVO orderVO = JSONUtil.toBean(orderJson, SeckillOrderVO.class);
					return SeckillExecuteResultVO.builder()
							.success(true)
							.orderVO(orderVO)
							.build();
				} catch (Exception e) {
					log.error("解析订单缓存失败, orderNo={}", orderNo, e);
				}
			}

			// 缓存不存在时，返回基本信息
			SeckillOrderVO basicOrder = SeckillOrderVO.builder()
					.orderNo(orderNo)
					.statusName("待支付")
					.build();
			return SeckillExecuteResultVO.builder()
					.success(true)
					.orderVO(basicOrder)
					.build();
		} else if (resultValue.startsWith("FAILED:") || resultValue.startsWith("ERROR:")) {
			String failReason = resultValue.contains(":") ?
					resultValue.substring(resultValue.indexOf(":") + 1) : "秒杀失败";
			throw new BizIllegalException(failReason);
		}

		return buildQueueResult(requestId);
	}

	/**
	 * 设置幂等处理结果
	 */
	private void setIdempotentResult(String idempotentKey, String result) {
		redisTemplate.opsForValue().set(
				idempotentKey,
				result,
				SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS,
				TimeUnit.SECONDS
		);
	}


	/**
	 * 验证并消费秒杀令牌（一次性使用）
	 * 使用 GET + DELETE 保证原子性
	 */
	private void validateSeckillToken(Long userId, String seckillToken,
									  Long activityId, Long seckillProductId) {
		String tokenKey = SeckillConstants.SECKILL_TOKEN_PREFIX + seckillToken;
		log.info("开始验证秒杀令牌, userId={}, token={}", userId, seckillToken);
		log.info("开始验证秒杀令牌, {}", tokenKey);


		// 核心：获取并立即删除令牌（防止重复使用）
		String tokenValue = redisTemplate.opsForValue().getAndDelete(tokenKey);

		// 令牌不存在或已被使用
		if (tokenValue == null) {
			log.warn("秒杀令牌不存在或已被使用, token={}, userId={}", seckillToken, userId);
			throw new BizIllegalException("秒杀令牌无效或已被使用");
		}

		// 解析令牌内容：userId:activityId:seckillProductId
		String[] parts = tokenValue.split(":");
		if (parts.length != 3) {
			log.error("令牌格式错误, token={}, value={}", seckillToken, tokenValue);
			throw new BizIllegalException("秒杀令牌无效");
		}

		try {
			Long tokenUserId = Long.parseLong(parts[0]);
			Long tokenActivityId = Long.parseLong(parts[1]);
			Long tokenProductId = Long.parseLong(parts[2]);

			// 验证用户ID
			if (!tokenUserId.equals(userId)) {
				log.warn("令牌用户不匹配, token={}, tokenUserId={}, requestUserId={}",
						seckillToken, tokenUserId, userId);
				throw new BizIllegalException("秒杀令牌无效");
			}

			// 验证活动ID
			if (!tokenActivityId.equals(activityId)) {
				log.warn("令牌活动不匹配, token={}, tokenActivityId={}, requestActivityId={}",
						seckillToken, tokenActivityId, activityId);
				throw new BizIllegalException("秒杀令牌无效");
			}

			// 验证商品ID
			if (!tokenProductId.equals(seckillProductId)) {
				log.warn("令牌商品不匹配, token={}, tokenProductId={}, requestProductId={}",
						seckillToken, tokenProductId, seckillProductId);
				throw new BizIllegalException("秒杀令牌无效");
			}

			log.info("一次性令牌验证并消费成功, token={}, userId={}", seckillToken, userId);

		} catch (NumberFormatException e) {
			log.error("令牌数据解析失败, token={}, value={}", seckillToken, tokenValue, e);
			throw new BizIllegalException("秒杀令牌无效");
		}
	}

	/**
	 * 从Redis获取活动信息
	 * Redis Key: seckill:activity:list:{activityId}
	 */
	private Map<String, String> getActivityFromRedis(Long activityId) {
		String activityInfoKey = SeckillConstants.SECKILL_ACTIVITY_LIST + activityId;
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(activityInfoKey);

		if (entries == null || entries.isEmpty()) {
			log.warn("活动不存在或未预热, activityId={}", activityId);
			throw new BizIllegalException("活动不存在或尚未预热");
		}

		Map<String, String> activityMap = new HashMap<>();
		entries.forEach((k, v) -> activityMap.put(k.toString(), v.toString()));
		return activityMap;
	}

	/**
	 * 从Redis获取商品信息
	 * Redis Key: seckill:product:{activityId}:{seckillProductId}
	 */
	private Map<String, String> getProductFromRedis(Long activityId, Long seckillProductId) {
		String productKey = SeckillConstants.SECKILL_PRODUCT_PREFIX + activityId + ":" + seckillProductId;
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(productKey);

		if (entries == null || entries.isEmpty()) {
			log.warn("秒杀商品不存在或未预热, seckillProductId={}, activityId={}",
					seckillProductId, activityId);
			throw new BizIllegalException("秒杀商品不存在或尚未预热");
		}

		Map<String, String> productMap = new HashMap<>();
		entries.forEach((k, v) -> productMap.put(k.toString(), v.toString()));
		return productMap;
	}

	/**
	 * 校验活动状态和时间窗口
	 * 1. 活动状态必须为进行中（status=1）
	 * 2. 当前时间必须在活动开始时间和结束时间之间
	 */
	private void validateActivity(Long activityId, Map<String, String> activity) {
		String statusStr = activity.get("status");
		if (statusStr == null) {
			log.warn("活动状态缺失, activityId={}", activityId);
			throw new BizIllegalException("活动状态异常");
		}

		Integer status = Integer.parseInt(statusStr);

		if (status != 1) {
			log.warn("活动状态异常, activityId={}, status={}", activityId, status);
			throw new BizIllegalException("活动不在进行中");
		}

		String startTimeStr = activity.get("startTime");
		String endTimeStr = activity.get("endTime");

		if (startTimeStr != null && endTimeStr != null) {
			try {
				LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
				LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
				LocalDateTime now = LocalDateTime.now();

				if (now.isBefore(startTime)) {
					log.warn("当前时间早于活动开始时间, activityId={}", activityId);
					throw new BizIllegalException("活动尚未开始");
				}

				if (now.isAfter(endTime)) {
					log.warn("当前时间晚于活动结束时间, activityId={}", activityId);
					throw new BizIllegalException("活动已结束");
				}
			} catch (Exception e) {
				log.error("解析活动时间失败, activityId={}", activityId, e);
			}
		}
	}

	/**
	 *
	 * 从商品信息中检查库存是否大于0
	 */
	private void validateProduct(Long activityId, Long seckillProductId) {
		String stockStr = redisTemplate.opsForValue().get(SeckillConstants.SECKILL_STOCK_PREFIX + activityId + ":" + seckillProductId);

		if (stockStr == null) {
			log.warn("商品库存信息缺失, seckillProductId={}", seckillProductId);
			throw new BizIllegalException("商品库存信息异常");
		}

		try {
			int stock = Integer.parseInt(stockStr);
			if (stock <= 0) {
				log.warn("商品库存不足, seckillProductId={}, stock={}", seckillProductId, stock);
				throw new BizIllegalException("商品已售罄");
			}
		} catch (NumberFormatException e) {
			log.error("解析库存失败, seckillProductId={}", seckillProductId, e);
			throw new BizIllegalException("商品库存信息异常");
		}
	}

	/**
	 * 校验用户购买限制
	 * 1. 商品级限购：检查用户已购买该商品的数量是否超过限购
	 * 2. 活动级限购：检查用户在活动中购买的总数量是否超过限购
	 */
	private void checkUserPurchaseLimit(Long userId, Long activityId,
										Long seckillProductId, Integer quantity
	) {
		// 从redis中获取用户活动级的已购买数（返回的是String）
		Object activityBuyObj = redisTemplate.opsForHash().get(
				SeckillConstants.SECKILL_USER_BUY_PREFIX + userId,
				"activity:" + activityId
		);

		// 从redis中获取用户商品级的已购买数（返回的是String）
		Object productBuyObj = redisTemplate.opsForHash().get(
				SeckillConstants.SECKILL_USER_BUY_PREFIX + userId,
				"product:" + seckillProductId
		);

		//先将Object转为String，再转为Long
		long activityBuy = 0L;
		if (activityBuyObj != null) {
			activityBuy = Long.parseLong(activityBuyObj.toString());
		}

		long productBuy = 0L;
		if (productBuyObj != null) {
			productBuy = Long.parseLong(productBuyObj.toString());
		}


		long activityCount = activityBuy + quantity;
		long productCount = productBuy + quantity;
		//判断是否超出活动级限购
		String activityLimit = redisTemplate.opsForValue().get(SeckillConstants.SECKILL_ACTIVITY_USER_LIMIT_PREFIX + activityId);
		if (activityLimit != null) {
			try {
				int limit = Integer.parseInt(activityLimit);
				if (activityCount > limit) {
					log.warn("用户超出活动级限购, userId={}, activityId={}, limit={}, count={}",
							userId, activityId, limit, activityCount);
					throw new BizIllegalException("超出活动级限购");
				}
			} catch (NumberFormatException e) {
				log.error("解析活动级限购失败, activityId={}", activityId, e);
			}
		}
		//判断是否超出商品级限购
		String productLimit = redisTemplate.opsForValue().get(SeckillConstants.SECKILL_PRODUCT_PREFIX + seckillProductId);
		if (productLimit != null) {
			try {
				int limit = Integer.parseInt(productLimit);
				if (productCount > limit) {
					log.warn("用户超出商品级限购, userId={}, seckillProductId={}, limit={}, count={}",
							userId, seckillProductId, limit, productCount);
					throw new BizIllegalException("超出商品级限购");
				}
			} catch (NumberFormatException e) {
				log.error("解析商品级限购失败, seckillProductId={}", seckillProductId, e);
			}
		}
	}

	/**
	 * 执行库存扣减（通过Lua脚本保证原子性）
	 * Lua脚本会原子性地完成以下操作：
	 * 1. 检查库存是否充足
	 * 2. 检查用户限购
	 * 3. 扣减库存
	 * 4. 更新用户购买记录
	 *
	 * 返回值：
	 * >= 0 : 扣减成功，返回剩余库存
	 * -1   : 库存不足
	 * -2   : 超过商品限购
	 * -3   : 超过活动限购
	 */
	/**
	 * 执行库存扣减（通过Lua脚本保证原子性）
	 */
	private Long executeStockDecrement(Long activityId, Long seckillProductId,
									   Long userId, Integer quantity) {

		// 构建 KEYS 列表
		List<String> keys = Arrays.asList(
				// KEYS[1]: 库存key
				SeckillConstants.SECKILL_STOCK_PREFIX + activityId + ":" + seckillProductId,
				// KEYS[2]: 用户购买记录key
				SeckillConstants.SECKILL_USER_BUY_PREFIX + userId,
				// KEYS[3]: 活动限购key
				SeckillConstants.SECKILL_ACTIVITY_USER_LIMIT_PREFIX + activityId,
				// KEYS[4]: 商品限购key
				SeckillConstants.SECKILL_USER_LIMIT_PREFIX + userId + ":" + seckillProductId
		);

		// ARGV 参数
		Object[] args = {
				String.valueOf(quantity),        // ARGV[1]
				String.valueOf(userId),          // ARGV[2]
				String.valueOf(activityId),      // ARGV[3]
				String.valueOf(seckillProductId) // ARGV[4]
		};

		log.info("执行Lua脚本扣减库存, keys={}, args={}", keys, args);

		// 执行 Lua 脚本
		Long result = redisTemplate.execute(SECKILL_EXECUTE_SCRIPT, keys, args);

		log.info("库存扣减结果: result={}, activityId={}, productId={}, userId={}, quantity={}",
				result, activityId, seckillProductId, userId, quantity);

		return result;
	}

	/**
	 *
	 * 同步模式处理：创建订单并返回成功响应
	 * 1. 生成订单号    //这里是预定单号
	 * 2. 计算订单金额
	 * 3. 设置支付超时时间（15分钟）
	 * 4. 记录幂等性信息到Redis
	 * 5. 设置订单超时取消监听
	 */
	private SeckillOrderVO handleSyncMode(Long userId, SeckillExecuteRequestDTO request,
										  Map<String, String> activity,
										  Map<String, String> product,
										  String idempotentKey) {
		String orderNo = generateOrderNo();

		BigDecimal seckillPrice = new BigDecimal(product.get("seckillPrice"));
		Integer quantity = request.getQuantity();
		BigDecimal totalAmount = seckillPrice.multiply(new BigDecimal(quantity));

		LocalDateTime payExpireTime = LocalDateTime.now().plusMinutes(SeckillConstants.ORDER_PAY_TIMEOUT_MINUTES);

		SeckillOrderVO orderVO = SeckillOrderVO.builder()
				.orderNo(orderNo)
				.seckillProductId(request.getSeckillProductId())
				.productName(product.get("name"))
				.seckillPrice(seckillPrice)
				.quantity(quantity)
				.totalAmount(totalAmount)
				.payExpireTime(payExpireTime)
				.status(0)
				.statusName("待支付")
				.build();

		redisTemplate.opsForValue().set(
				idempotentKey,
				orderNo,
				SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS,
				TimeUnit.SECONDS
		);

		redisTemplate.opsForValue().set(
				SeckillConstants.SECKILL_ORDER_TIMEOUT_PREFIX + orderNo,
				String.valueOf(userId),
				SeckillConstants.ORDER_PAY_TIMEOUT_MINUTES,
				TimeUnit.MINUTES
		);

		log.info("秒杀预订单订单创建成功, userId={}, orderNo={}, seckillProductId={}",
				userId, orderNo, request.getSeckillProductId());

		return orderVO;
	}

	/**
	 * 构建重复请求的响应结果
	 * 当检测到requestId已存在时，返回之前的订单信息
	 */
	private SeckillExecuteResultVO buildDuplicateResult(String orderNo) {
		SeckillOrderVO orderVO = SeckillOrderVO.builder()
				.orderNo(orderNo)
				.statusName("待支付")
				.build();

		return SeckillExecuteResultVO.builder()
				.success(true)
				.orderVO(orderVO)
				.build();
	}

	/**
	 * 生成订单号
	 * 格式：时间戳 + 机器ID + 序列号
	 * 示例：SK191238123812381238
	 */
	private String generateOrderNo() {
		//
		return "SK" + IdUtil.getSnowflakeNextIdStr();
	}
}
