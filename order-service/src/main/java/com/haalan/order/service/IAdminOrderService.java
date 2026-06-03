package com.haalan.order.service;

import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.dto.AdminOrderQueryDTO;
import com.haalan.order.domain.dto.OrderStatusUpdateDTO;
import com.haalan.order.domain.vo.AdminOrderListVO;

import java.util.Map;

public interface IAdminOrderService {

	PageResult<AdminOrderListVO> listOrders(AdminOrderQueryDTO query);

	Map<String, Object> getOrderDetail(String orderNo);

	void updateOrderStatus(String orderNo, OrderStatusUpdateDTO dto);

	PageResult<AdminOrderListVO> listSeckillOrders(AdminOrderQueryDTO query);

	Map<String, Object> getSeckillOrderDetail(String orderNo);
}
