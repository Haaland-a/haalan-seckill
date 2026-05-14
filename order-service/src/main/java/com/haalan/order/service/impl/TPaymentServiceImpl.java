package com.haalan.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.config.AlipayProperties;
import com.haalan.order.config.MQProperties;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.PayResponseVO;
import com.haalan.order.mapper.TPaymentMapper;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.mapper.TSeckillOrder1Mapper;
import com.haalan.order.service.ITPaymentService;
import com.haalan.order.service.ITSeckillOrder0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayResponseVO createPayment(Long userId, PayRequestDTO request) {
		String orderNo = request.getOrderNo();
		Integer payType = request.getPayType();

		log.info("开始创建支付订单, userId={}, orderNo={}, payType={}", userId, orderNo, payType);

		// 1. 查询订单信息（根据userId确定分表）
		int tableSuffix = (int) (userId % 2);
		TSeckillOrder order;
		if (tableSuffix == 0) {
			order = seckillOrder0Mapper.selectOne(
					new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		} else {
			order = seckillOrder1Mapper.selectOne(
					new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		}

		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}

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

	/**
	 * 生成支付宝支付URL（真实调用）
	 */
	private String generateAlipayPayUrl(Long userId, String orderNo, String productName, BigDecimal payAmount) {
		try {
			// 1. 初始化支付宝客户端
			AlipayConfig config = new AlipayConfig();

			config.setServerUrl(alipayProperties.getServerUrl());
			config.setAppId(alipayProperties.getAppId());
			config.setPrivateKey(alipayProperties.getPrivateKey());
			config.setFormat("json");
			config.setCharset("UTF-8");
			config.setAlipayPublicKey(alipayProperties.getAlipayPublicKey());
			config.setSignType("RSA2");

			AlipayClient alipayClient = new DefaultAlipayClient(config);


			System.out.println(alipayProperties.getPrivateKey());
			System.out.println(alipayProperties.getAlipayPublicKey());


			// 2. 创建请求对象
			AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

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
			String timeoutExpress = timeOut != null ? String.valueOf(timeOut / 1000 / 60) + "m" : "30m";
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

	/**
	 * 生成微信支付URL（模拟）
	 */
	private String generateWechatPayUrl(String orderNo) {
		String transactionId = IdUtil.fastSimpleUUID();
		return "weixin://wxpay/bizpayurl?pr=" + transactionId;
	}

	/**
	 * 生成银行卡支付URL（模拟）
	 */
	private String generateBankPayUrl(String orderNo) {
		String transactionId = IdUtil.fastSimpleUUID();
		return "https://unionpay.com/pay?order=" + transactionId;
	}

	/**
	 * 生成二维码Base64（模拟）
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
		switch (payType) {
			case 1:
				return "微信支付";
			case 2:
				return "支付宝";
			case 3:
				return "银行卡";
			default:
				return "未知";
		}
	}
}
