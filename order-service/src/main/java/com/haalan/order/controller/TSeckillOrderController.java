package com.haalan.order.controller;


import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.domain.dto.CancelOrderRequestDTO;
import com.haalan.order.domain.vo.CancelOrderResponseVO;
import com.haalan.order.domain.vo.OrderDetailVO;
import com.haalan.order.domain.vo.OrderListItemVO;
import com.haalan.order.service.ITOrderService;
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
public class TSeckillOrderController {

	private final ITSeckillOrder0Service seckillOrderService;
	private final ITOrderService orderService;

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

	@GetMapping("/seckill/detail/{orderNo}")
	@ApiOperation("获取订单详情")
	public R<OrderDetailVO> getOrderDetail(@PathVariable String orderNo) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 查询订单详情
		OrderDetailVO orderDetail = seckillOrderService.getOrderDetail(orderNo, userId);

		return R.success(orderDetail);
	}

	@GetMapping("/seckill/list")
	@ApiOperation("获取秒杀订单列表")
	public R<PageResult<OrderListItemVO>> getSeckillOrderList(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize,
			@RequestParam(required = false) Integer status) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 查询秒杀订单列表
		PageResult<OrderListItemVO> orderList = seckillOrderService.getSeckillOrderList(userId, pageNum, pageSize, status);

		return R.success(orderList);
	}

	@GetMapping("/list")
	@ApiOperation("获取普通订单列表")
	public R<PageResult<OrderListItemVO>> getNormalOrderList(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize,
			@RequestParam(required = false) Integer status) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 查询普通订单列表
		PageResult<OrderListItemVO> orderList = orderService.getNormalOrderList(userId, pageNum, pageSize, status);

		return R.success(orderList);
	}
}
