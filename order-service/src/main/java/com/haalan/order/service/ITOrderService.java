package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.dto.CreateOrderRequestDTO;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.vo.CancelOrderResponseVO;
import com.haalan.order.domain.vo.OrderDetailVO;
import com.haalan.order.domain.vo.OrderListItemVO;

/**
 * <p>
 * 订单主表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
public interface ITOrderService extends IService<TOrder> {

	/**
	 * 获取普通订单列表
	 *
	 * @param userId   用户ID
	 * @param pageNum  页码
	 * @param pageSize 每页数量
	 * @param status   订单状态（可选）
	 * @return 订单列表分页结果
	 */
	PageResult<OrderListItemVO> getNormalOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status);

	/**
	 * 创建普通订单
	 *
	 * @param userId  用户ID
	 * @param request 创建订单请求
	 * @return 订单详情
	 */
	OrderDetailVO createOrder(Long userId, CreateOrderRequestDTO request);

	/**
	 * 获取普通订单详情
	 *
	 * @param orderNo 订单号
	 * @param userId  用户ID
	 * @return 订单详情
	 */
	OrderDetailVO getOrderDetail(String orderNo, Long userId);

	/**
	 * 取消普通订单
	 *
	 * @param orderNo      订单号
	 * @param userId       用户ID
	 * @param cancelReason 取消原因
	 * @return 取消结果
	 */
	CancelOrderResponseVO cancelOrder(String orderNo, Long userId, String cancelReason);
}
