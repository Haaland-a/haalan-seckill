package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.po.TOrder;
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

}
