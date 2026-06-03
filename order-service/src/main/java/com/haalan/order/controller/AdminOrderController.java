package com.haalan.order.controller;

import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.order.domain.dto.AdminOrderQueryDTO;
import com.haalan.order.domain.dto.OrderStatusUpdateDTO;
import com.haalan.order.domain.vo.AdminOrderListVO;
import com.haalan.order.service.IAdminOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Api(tags = "管理端订单管理")
public class AdminOrderController {

	private final IAdminOrderService adminOrderService;

	@GetMapping
	@ApiOperation("订单列表（普通订单）")
	public R<PageResult<AdminOrderListVO>> listOrders(AdminOrderQueryDTO query) {
		return R.success(adminOrderService.listOrders(query));
	}

	@GetMapping("/{orderNo}")
	@ApiOperation("订单详情")
	public R<Map<String, Object>> getOrderDetail(@PathVariable String orderNo) {
		return R.success(adminOrderService.getOrderDetail(orderNo));
	}

	@PutMapping("/{orderNo}/status")
	@ApiOperation("修改订单状态")
	public R<Void> updateOrderStatus(@PathVariable String orderNo,
									 @RequestBody @Validated OrderStatusUpdateDTO dto) {
		adminOrderService.updateOrderStatus(orderNo, dto);
		return R.success("状态更新成功");
	}

	@GetMapping("/seckill")
	@ApiOperation("秒杀订单列表")
	public R<PageResult<AdminOrderListVO>> listSeckillOrders(AdminOrderQueryDTO query) {
		return R.success(adminOrderService.listSeckillOrders(query));
	}

	@GetMapping("/seckill/{orderNo}")
	@ApiOperation("秒杀订单详情")
	public R<Map<String, Object>> getSeckillOrderDetail(@PathVariable String orderNo) {
		return R.success(adminOrderService.getSeckillOrderDetail(orderNo));
	}
}
