package com.haalan.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.domain.mq.SeckillOrderPaySuccessMessage;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.config.AlipayProperties;
import com.haalan.order.config.MQProperties;
import com.haalan.order.config.RabbitConstants;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.PayResponseVO;
import com.haalan.order.domain.vo.PayResultVO;
import com.haalan.order.mapper.TPaymentMapper;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.mapper.TSeckillOrder1Mapper;
import com.haalan.order.service.IMessagePushService;
import com.haalan.order.service.ITPaymentService;
import com.haalan.order.service.ITSeckillOrder0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * <p>
 * 支付表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TPaymentServiceImpl extends ServiceImpl<TPaymentMapper, TPayment> implements ITPaymentService {
	private final ITSeckillOrder0Service seckillOrderService;
	private final TSeckillOrder0Mapper seckillOrder0Mapper;
	private final TSeckillOrder1Mapper seckillOrder1Mapper;

	@Resource
	private AlipayProperties alipayProperties;

	@Resource
	private MQProperties mqProperties;

	@Resource
	private IMessagePushService messagePushService;

	@Resource
	private RabbitTemplate rabbitTemplate;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayResponseVO createPayment(Long userId, PayRequestDTO request) {
		String orderNo = request.getOrderNo();
		Integer payType = request.getPayType();

		log.info("开始创建支付订单, userId={}, orderNo={}, payType={}", userId, orderNo, payType);

		// 1. 查询订单信息（根据userId确定分表）
		TSeckillOrder order = selectOrder(userId, orderNo);

		// 2. 验证订单状态
		if (order.getStatus() != 0) {
			throw new BizIllegalException("订单状态异常，无法支付");
		}

		// 3. 验证用户权限
		if (!order.getUserId().equals(userId)) {
			throw new BizIllegalException("无权操作此订单");
		}

		// 4. 检查是否已存在支付记录
		TPayment existingPayment = this.lambdaQuery()
				.eq(TPayment::getOrderNo, orderNo)
				.one();

		if (existingPayment != null && existingPayment.getStatus() == 1) {
			throw new BizIllegalException("订单已支付");
		}

		// 5. 创建或更新支付记录
		BigDecimal payAmount = order.getTotalAmount();
		TPayment payment;
		if (existingPayment == null) {
			payment = new TPayment();
			payment.setOrderNo(orderNo);
			payment.setUserId(userId);
			payment.setPayAmount(payAmount);
			payment.setPayType(payType);
			payment.setStatus(0); // 待支付
			this.save(payment);
		} else {
			payment = existingPayment;
			payment.setPayType(payType);
			this.updateById(payment);
		}

		// 6. 生成支付URL（根据支付方式调用不同的支付接口）
		String payUrl;
		String qrCode = null;
		if (payType == 2) {
			// 支付宝支付
			payUrl = generateAlipayPayUrl(userId, orderNo, order.getProductName(), payAmount);
		} else if (payType == 1) {
			// 微信支付（暂时使用模拟URL）
			payUrl = generateWechatPayUrl(orderNo);
			qrCode = generateQrCode(payUrl);
		} else {
			// 银行卡支付（暂时使用模拟URL）
			payUrl = generateBankPayUrl(orderNo);
		}

		// 7. 计算支付过期时间
		LocalDateTime expireTime = LocalDateTime.now().plusMinutes(15);

		log.info("支付订单创建成功, orderNo={}, payAmount={}, payUrl={}", orderNo, payAmount, payUrl);

		// 8. 构建响应
		return PayResponseVO.builder()
				.orderNo(orderNo)
				.payAmount(payAmount)
				.payType(payType)
				.payTypeName(getPayTypeName(payType))
				.payUrl(payUrl)
				.qrCode(qrCode)
				.expireTime(expireTime)
				.build();
	}

	private TSeckillOrder selectOrder(Long userId, String orderNo) {
		int tableSuffix = (int) (userId % 2);
		TSeckillOrder order;
		if (tableSuffix == 0) {
			order = seckillOrder0Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		} else {
			order = seckillOrder1Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		}

		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}
		return order;
	}

	/**
	 * 生成支付宝支付URL（真实调用）
	 */
	private String generateAlipayPayUrl(Long userId, String orderNo, String productName, BigDecimal payAmount) {
		try {
			// 1. 初始化支付宝客户端

			AlipayClient alipayClient = initAlipayClient();


			System.out.println(alipayProperties.getPrivateKey());
			System.out.println(alipayProperties.getAlipayPublicKey());


			// 2. 创建请求对象
			AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
			request.setNotifyUrl(alipayProperties.getNotifyUrl());

			// 3. 设置业务参数
			AlipayTradePagePayModel model = new AlipayTradePagePayModel();
			model.setOutTradeNo(orderNo); // 商户订单号
			// 订单金额精确到两位小数
			payAmount = payAmount.setScale(2, RoundingMode.HALF_UP);
			model.setTotalAmount(payAmount.toString()); // 订单金额

			String productNameResult = productName.replaceAll("[^a-zA-Z0-9_]", "");

			model.setSubject(productNameResult); // 订单标题
			//从数据库获取
			TSeckillOrder allByNo = seckillOrderService.getAllByNo(orderNo, userId);
			String alipayProductCode = allByNo.getAlipayProductCode();
			model.setProductCode(alipayProductCode != null ? alipayProductCode : "FAST_INSTANT_TRADE_PAY"); // 销售产品码
			model.setQrPayMode("1"); // PC扫码支付模式
			Integer timeOut = mqProperties.getTimeOut();
			// 超时时间
			String timeoutExpress = timeOut != null ? timeOut / 1000 / 60 + "m" : "30m";
			model.setTimeoutExpress(timeoutExpress); // 超时时间

//			// 设置商品详情（可选，但建议设置）
//			List<GoodsDetail> goodsDetailList = new ArrayList<>();
//			GoodsDetail goodsDetail = new GoodsDetail();
//			goodsDetail.setGoodsName(productName);
//			goodsDetail.setQuantity(1L);
//			goodsDetail.setPrice(payAmount.toString());
//			// 如果有skuId可以设置
//			if (allByNo.getSkuId() != null) {
//				goodsDetail.setOutSkuId(String.valueOf(allByNo.getSkuId()));
//			}
//			goodsDetailList.add(goodsDetail);
//			model.setGoodsDetail(goodsDetailList);

			request.setBizModel(model);

			// 4. 执行请求，获取表单HTML
			AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "GET");
			log.info("支付宝请求体: {}", response.getBody());
			if (response.isSuccess()) {
				log.info("支付宝支付表单生成成功, orderNo={}", orderNo);
				// 返回的是完整的HTML表单，前端可以直接展示或提交
				return response.getBody();
			} else {
				log.error("支付宝支付表单生成失败, orderNo={}, subCode={}, subMsg={}",
						orderNo, response.getSubCode(), response.getSubMsg());
				throw new BizIllegalException("支付宝支付发起失败: " + response.getSubMsg());
			}

		} catch (AlipayApiException e) {
			log.error("调用支付宝API异常, orderNo={}", orderNo, e);
			throw new BizIllegalException("支付宝支付发起失败");
		}
	}

	private AlipayClient initAlipayClient() throws AlipayApiException {
		AlipayConfig config = new AlipayConfig();

		config.setServerUrl(alipayProperties.getServerUrl());
		config.setAppId(alipayProperties.getAppId());
		config.setPrivateKey(alipayProperties.getPrivateKey());
		config.setFormat("json");
		config.setCharset("UTF-8");
		config.setAlipayPublicKey(alipayProperties.getAlipayPublicKey());
		config.setSignType("RSA2");
		return new DefaultAlipayClient(config);
	}

	/**
	 * 生成微信支付URL
	 */
	private String generateWechatPayUrl(String orderNo) {
		String transactionId = IdUtil.fastSimpleUUID();
		return "weixin://wxpay/bizpayurl?pr=" + transactionId;
	}

	/**
	 * 生成银行卡支付URL
	 */
	private String generateBankPayUrl(String orderNo) {
		String transactionId = IdUtil.fastSimpleUUID();
		return "https://unionpay.com/pay?order=" + transactionId;
	}

	/**
	 * 生成二维码Base64
	 */
	private String generateQrCode(String payUrl) {
		// 实际项目中应该调用二维码生成工具
		// 这里返回一个模拟的Base64字符串
		return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
	}

	/**
	 * 获取支付方式名称
	 */
	private String getPayTypeName(Integer payType) {
		return switch (payType) {
			case 1 -> "微信支付";
			case 2 -> "支付宝";
			case 3 -> "银行卡";
			default -> "未知";
		};
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String handleAlipayCallback(String outTradeNo, String tradeNo, String totalAmount) {
		log.info("处理支付宝回调 - 订单号: {}, 支付宝交易号: {}, 金额: {}", outTradeNo, tradeNo, totalAmount);

		try {
			// 1. 查询支付记录获取userId
			TPayment payment = this.lambdaQuery()
					.eq(TPayment::getOrderNo, outTradeNo)
					.one();

			if (payment == null) {
				log.error("支付记录不存在: {}", outTradeNo);
				return "fail";
			}

			// 如果已经处理过，直接返回成功
			if (payment.getStatus() == 1) {
				log.info("订单已处理，跳过: {}", outTradeNo);
				return "success";
			}

			Long userId = payment.getUserId();

			// 2. 根据userId确定分表，查询订单
			int tableSuffix = (int) (userId % 2);
			TSeckillOrder order;
			if (tableSuffix == 0) {
				order = seckillOrder0Mapper.selectOne(
						new LambdaQueryWrapper<TSeckillOrder>()
								.eq(TSeckillOrder::getOrderNo, outTradeNo)
				);
			} else {
				order = seckillOrder1Mapper.selectOne(
						new LambdaQueryWrapper<TSeckillOrder>()
								.eq(TSeckillOrder::getOrderNo, outTradeNo)
				);
			}

			if (order == null) {
				log.error("订单不存在: {}", outTradeNo);
				return "fail";
			}

			// 3. 更新订单状态为已支付
			order.setStatus(1); // 已支付
			order.setPayTime(LocalDateTime.now());

			boolean orderUpdated;
			if (tableSuffix == 0) {
				orderUpdated = seckillOrder0Mapper.updateById(order) > 0;
			} else {
				orderUpdated = seckillOrder1Mapper.updateById(order) > 0;
			}

			if (!orderUpdated) {
				log.error("更新订单状态失败: {}", outTradeNo);
				return "fail";
			}

			// 4. 更新支付记录
			payment.setStatus(1); // 已支付
			payment.setTransactionId(tradeNo);
			payment.setPayTime(LocalDateTime.now());
			this.updateById(payment);

			log.info("订单支付成功处理完成: orderNo={}, tradeNo={}", outTradeNo, tradeNo);

			// 5. 推送WebSocket消息通知客户端
			messagePushService.pushPaymentSuccess(userId, outTradeNo);

			// 6. 发送MQ消息通知秒杀服务更新秒杀记录状态
			sendPaySuccessMessage(order, userId);

			return "success";

		} catch (Exception e) {
			log.error("处理支付宝回调时发生错误", e);
			return "fail";
		}
	}

	@Override
	public PayResultVO getPayResult(String orderNo, Long userId) {
		log.info("查询支付结果, orderNo={}, userId={}", orderNo, userId);
//由于支付宝不给我回调这部分可以忽略
		// 1. 查询支付记录
		TPayment payment = this.lambdaQuery()
				.eq(TPayment::getOrderNo, orderNo)
				.one();

		if (payment == null) {
			throw new BizIllegalException("支付记录不存在");
		}

		// 2. 验证用户权限
		if (!payment.getUserId().equals(userId)) {
			throw new BizIllegalException("无权查看此订单的支付结果");
		}

		// 3. 如果本地状态是待支付，且支付方式是支付宝，则主动调用支付宝查询接口
		if (payment.getStatus() == 0 && payment.getPayType() == 2) {
			log.info("订单待支付且使用支付宝，主动查询支付宝交易状态, orderNo={}", orderNo);
			queryAndUpdateAlipayStatus(payment);
			// 重新查询最新的支付记录
			payment = this.lambdaQuery()
					.eq(TPayment::getOrderNo, orderNo)
					.one();
		}

		// 4. 构建支付结果VO
		return PayResultVO.builder()
				.orderNo(payment.getOrderNo())
				.payStatus(payment.getStatus())
				.payStatusName(getPayStatusName(payment.getStatus()))
				.payAmount(payment.getPayAmount())
				.payType(payment.getPayType())
				.payTypeName(getPayTypeName(payment.getPayType()))
				.transactionId(payment.getTransactionId())
				.payTime(payment.getPayTime())
				.build();
	}

	/**
	 * 主动查询支付宝交易状态并更新本地记录
	 */
	private void queryAndUpdateAlipayStatus(TPayment payment) {
		try {
			// 1. 初始化支付宝客户端
			AlipayClient alipayClient = initAlipayClient();

			// 2. 构造查询请求
			AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
			AlipayTradeQueryModel model = new AlipayTradeQueryModel();
			model.setOutTradeNo(payment.getOrderNo()); // 商户订单号
			request.setBizModel(model);

			// 3. 执行查询
			AlipayTradeQueryResponse response = alipayClient.execute(request);
			log.info("支付宝查询响应: {}", response.getBody());

			if (response.isSuccess()) {
				String tradeStatus = response.getTradeStatus();
				log.info("支付宝交易状态: {}, orderNo={}", tradeStatus, payment.getOrderNo());

				// 4. 根据支付宝返回的状态更新本地记录
				if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
					// 支付成功
					updatePaymentSuccess(payment, response.getTradeNo(), response.getTotalAmount());
				} else if ("TRADE_CLOSED".equals(tradeStatus)) {
					// 交易关闭
					updatePaymentFailed(payment, "交易已关闭");
				} else if ("WAIT_BUYER_PAY".equals(tradeStatus)) {
					// 等待买家付款，保持待支付状态
					log.info("订单等待买家付款, orderNo={}", payment.getOrderNo());
				} else {
					log.warn("未知的支付宝交易状态: {}, orderNo={}", tradeStatus, payment.getOrderNo());
				}
			} else {
				log.error("支付宝查询失败, orderNo={}, subCode={}, subMsg={}",
						payment.getOrderNo(), response.getSubCode(), response.getSubMsg());
			}
		} catch (AlipayApiException e) {
			log.error("调用支付宝查询API异常, orderNo={}", payment.getOrderNo(), e);
		}
	}

	/**
	 * 更新支付记录为成功状态
	 */
	@Transactional(rollbackFor = Exception.class)
	public void updatePaymentSuccess(TPayment payment, String tradeNo, String totalAmount) {
		log.info("更新支付记录为成功, orderNo={}, tradeNo={}", payment.getOrderNo(), tradeNo);

		// 1. 更新支付记录
		payment.setStatus(1); // 已支付
		payment.setTransactionId(tradeNo);
		payment.setPayTime(LocalDateTime.now());
		this.updateById(payment);

		// 2. 查询订单并更新订单状态
		Long userId = payment.getUserId();
		int tableSuffix = (int) (userId % 2);
		TSeckillOrder order;
		if (tableSuffix == 0) {
			order = seckillOrder0Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, payment.getOrderNo())
			);
		} else {
			order = seckillOrder1Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, payment.getOrderNo())
			);
		}

		if (order != null && order.getStatus() == 0) {
			// 只有待支付的订单才更新
			order.setStatus(1); // 已支付
			order.setPayTime(LocalDateTime.now());
			if (tableSuffix == 0) {
				seckillOrder0Mapper.updateById(order);
			} else {
				seckillOrder1Mapper.updateById(order);
			}
			log.info("订单状态更新为已支付, orderNo={}", payment.getOrderNo());

			// 3. 推送WebSocket消息通知客户端
			messagePushService.pushPaymentSuccess(userId, payment.getOrderNo());

			// 4. 发送MQ消息通知秒杀服务更新秒杀记录状态
			sendPaySuccessMessage(order, userId);
		}
	}

	/**
	 * 更新支付记录为失败状态
	 */
	@Transactional(rollbackFor = Exception.class)
	public void updatePaymentFailed(TPayment payment, String reason) {
		log.info("更新支付记录为失败, orderNo={}, reason={}", payment.getOrderNo(), reason);
		payment.setStatus(2); // 支付失败
		this.updateById(payment);
	}

	/**
	 * 获取支付状态名称
	 */
	private String getPayStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		return switch (status) {
			case 0 -> "待支付";
			case 1 -> "已支付";
			case 2 -> "支付失败";
			default -> "未知";
		};
	}

	/**
	 * 发送支付成功MQ消息，通知秒杀服务更新秒杀记录状态
	 *
	 * @param order  订单信息
	 * @param userId 用户ID
	 */
	private void sendPaySuccessMessage(TSeckillOrder order, Long userId) {
		try {
			SeckillOrderPaySuccessMessage message = SeckillOrderPaySuccessMessage.builder()
					.orderNo(order.getOrderNo())
					.userId(userId)
					.activityId(order.getActivityId())
					.seckillProductId(order.getSeckillProductId())
					.build();

			rabbitTemplate.convertAndSend(
					RabbitConstants.SECKILL_PAY_SUCCESS_EXCHANGE,
					RabbitConstants.SECKILL_PAY_SUCCESS_ROUTING_KEY,
					message
			);

			log.info("支付成功MQ消息已发送, orderNo={}, userId={}", order.getOrderNo(), userId);

		} catch (Exception e) {
			// MQ消息发送失败不影响主流程，记录日志即可
			log.error("发送支付成功MQ消息失败, orderNo={}, userId={}", order.getOrderNo(), userId, e);
		}
	}
}
