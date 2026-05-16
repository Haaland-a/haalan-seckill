package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.domain.vo.PayResponseVO;
import com.haalan.order.domain.vo.PayResultVO;

/**
 * <p>
 * 支付表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
public interface ITPaymentService extends IService<TPayment> {

	/**
	 * 创建支付订单
	 *
	 * @param userId  用户ID
	 * @param request 支付请求
	 * @return 支付响应
	 */
	PayResponseVO createPayment(Long userId, PayRequestDTO request);

	/**
	 * 处理支付宝异步通知回调
	 *
	 * @param outTradeNo  商户订单号
	 * @param tradeNo     支付宝交易号
	 * @param totalAmount 付款金额
	 * @return 处理结果（success或fail）
	 */
	String handleAlipayCallback(String outTradeNo, String tradeNo, String totalAmount);

	/**
	 * 查询支付结果
	 *
	 * @param orderNo 订单号
	 * @param userId  用户ID
	 * @return 支付结果
	 */
	PayResultVO getPayResult(String orderNo, Long userId);
}
