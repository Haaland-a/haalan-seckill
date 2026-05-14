package com.haalan.order.controller;


import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.dto.CancelOrderRequestDTO;
import com.haalan.order.domain.vo.CancelOrderResponseVO;
import com.haalan.order.service.ITSeckillOrder0Service;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 秒杀订单表 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Api(tags = "秒杀订单管理")
public class TSeckillOrder0Controller {

	private final ITSeckillOrder0Service seckillOrderService;

	@PostMapping("/cancel/{orderNo}")
	@ApiOperation("取消订单")
	public R<CancelOrderResponseVO> cancelOrder(
			@PathVariable String orderNo,
			@RequestBody @Validated CancelOrderRequestDTO request) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 取消订单
		CancelOrderResponseVO response = seckillOrderService.cancelOrder(orderNo, userId, request.getCancelReason());

		return R.success(response);
	}
}
