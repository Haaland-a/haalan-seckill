package com.haalan.order.controller;

import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.dto.CancelOrderRequestDTO;
import com.haalan.order.domain.dto.CreateOrderRequestDTO;
import com.haalan.order.domain.vo.CancelOrderResponseVO;
import com.haalan.order.domain.vo.OrderDetailVO;
import com.haalan.order.domain.vo.OrderListItemVO;
import com.haalan.order.service.ITOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 普通订单 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-30
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Api(tags = "普通订单管理")
public class TOrderController {

	private final ITOrderService orderService;

	@PostMapping("/create")
	@ApiOperation("创建普通订单")
	public R<OrderDetailVO> createOrder(@RequestBody @Validated CreateOrderRequestDTO request) {
		Long userId = UserContext.getUser();
		OrderDetailVO orderDetail = orderService.createOrder(userId, request);
		return R.success("下单成功", orderDetail);
	}

	@GetMapping("/detail/{orderNo}")
	@ApiOperation("获取普通订单详情")
	public R<OrderDetailVO> getOrderDetail(@PathVariable String orderNo) {
		Long userId = UserContext.getUser();
		OrderDetailVO orderDetail = orderService.getOrderDetail(orderNo, userId);
		return R.success(orderDetail);
	}

	@PostMapping("/cancel/{orderNo}")
	@ApiOperation("取消普通订单")
	public R<CancelOrderResponseVO> cancelOrder(
			@PathVariable String orderNo,
			@RequestBody @Validated CancelOrderRequestDTO request) {
		Long userId = UserContext.getUser();
		CancelOrderResponseVO response = orderService.cancelOrder(orderNo, userId, request.getCancelReason());
		return R.success("取消成功", response);
	}

	@GetMapping("/list")
	@ApiOperation("获取普通订单列表")
	public R<PageResult<OrderListItemVO>> getNormalOrderList(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize,
			@RequestParam(required = false) Integer status) {
		Long userId = UserContext.getUser();
		PageResult<OrderListItemVO> orderList = orderService.getNormalOrderList(userId, pageNum, pageSize, status);
		return R.success(orderList);
	}
}
