package com.haalan.order.controller;

import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.vo.PayResponseVO;
import com.haalan.order.service.ITPaymentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 支付表 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Api(tags = "订单支付")
public class TPaymentController {

	private final ITPaymentService paymentService;

	@PostMapping("/pay")
	@ApiOperation("创建支付订单")
	public R<PayResponseVO> createPayment(@RequestBody @Validated PayRequestDTO request) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 创建支付订单
		PayResponseVO response = paymentService.createPayment(userId, request);

		return R.success(response);
	}
}
