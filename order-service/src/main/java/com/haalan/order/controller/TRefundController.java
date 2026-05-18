package com.haalan.order.controller;

import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.dto.RefundRequestDTO;
import com.haalan.order.domain.vo.RefundResponseVO;
import com.haalan.order.service.ITRefundService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 退款管理 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Api(tags = "退款管理")
public class TRefundController {

	private final ITRefundService refundService;

	@PostMapping("/refund")
	@ApiOperation("申请退款")
	public R<RefundResponseVO> applyRefund(@RequestBody @Validated RefundRequestDTO request) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 申请退款
		RefundResponseVO response = refundService.applyRefund(userId, request);

		return R.success("退款申请已提交", response);
	}


}
