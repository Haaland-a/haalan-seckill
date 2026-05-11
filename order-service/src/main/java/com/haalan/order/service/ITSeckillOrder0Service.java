package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.order.domain.po.OrderTimeoutMessage;
import com.haalan.order.domain.po.SeckillOrderMessage;
import com.haalan.order.domain.po.TSeckillOrder;

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
}
