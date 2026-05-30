package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.order.domain.po.TOrderItem;

import java.util.List;

/**
 * <p>
 * 订单商品明细表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
public interface ITOrderItemService extends IService<TOrderItem> {

	/**
	 * 根据订单号查询订单商品列表
	 *
	 * @param orderNo 订单号
	 * @return 订单商品列表
	 */
	List<TOrderItem> getByOrderNo(String orderNo);
}
