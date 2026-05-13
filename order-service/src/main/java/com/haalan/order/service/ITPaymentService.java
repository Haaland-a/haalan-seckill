package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.domain.vo.PayResponseVO;

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
}
