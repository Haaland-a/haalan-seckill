package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.mq.OrderTimeoutMessage;
import com.haalan.common.domain.mq.SeckillOrderMessage;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.CancelOrderResponseVO;

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

}
