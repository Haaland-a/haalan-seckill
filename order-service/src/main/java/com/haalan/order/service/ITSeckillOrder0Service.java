package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderMessage;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.CancelOrderResponseVO;
import com.haalan.order.domain.vo.OrderDetailVO;
import com.haalan.order.domain.vo.OrderListItemVO;

/**
 * <p>
 * 秒杀订单表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
public interface ITSeckillOrder0Service extends IService<TSeckillOrder> {

	void saveMsg(SeckillOrderMessage message);

	void setStatus(OrderTimeoutMessage message);

	TSeckillOrder getAllByNo(String orderNo, Long userId);

	/**
	 * 取消订单
	 *
	 * @param orderNo      订单号
	 * @param userId       用户ID
	 * @param cancelReason 取消原因
	 * @return 取消订单响应
	 */
	CancelOrderResponseVO cancelOrder(String orderNo, Long userId, String cancelReason);

	/**
	 * 获取订单详情（支持普通订单和秒杀订单）
	 *
	 * @param orderNo 订单号
	 * @param userId  用户ID
	 * @return 订单详情
	 */
	OrderDetailVO getOrderDetail(String orderNo, Long userId);

	/**
	 * 获取秒杀订单列表
	 *
	 * @param userId   用户ID
	 * @param pageNum  页码
	 * @param pageSize 每页数量
	 * @param status   订单状态（可选）
	 * @return 订单列表分页结果
	 */
	PageResult<OrderListItemVO> getSeckillOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status);

}
