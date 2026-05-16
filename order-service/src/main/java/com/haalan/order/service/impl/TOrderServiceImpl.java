package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.domain.vo.OrderListItemVO;
import com.haalan.order.mapper.TOrderMapper;
import com.haalan.order.service.ITOrderItemService;
import com.haalan.order.service.ITOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单主表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Service
@RequiredArgsConstructor
public class TOrderServiceImpl extends ServiceImpl<TOrderMapper, TOrder> implements ITOrderService {

	private final ITOrderItemService orderItemService;
	private final ItemServiceClient itemServiceClient;

	@Override
	public PageResult<OrderListItemVO> getNormalOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status) {
		// 1. 构建分页对象
		Page<TOrder> page = new Page<>(pageNum, pageSize);

		// 2. 构建查询条件
		LambdaQueryWrapper<TOrder> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(TOrder::getUserId, userId);
		if (status != null) {
			queryWrapper.eq(TOrder::getStatus, status);
		}
		queryWrapper.orderByDesc(TOrder::getCreateTime);

		// 3. 执行分页查询
		Page<TOrder> orderPage = this.page(page, queryWrapper);

		// 4. 转换为VO
		List<OrderListItemVO> voList = orderPage.getRecords().stream()
				.map(order -> {
					OrderListItemVO vo = new OrderListItemVO();
					vo.setOrderNo(order.getOrderNo());
					vo.setOrderType(order.getOrderType() != null ? order.getOrderType() : 1); // 默认普通订单
					vo.setOrderTypeName(getOrderTypeName(vo.getOrderType()));
					vo.setTotalAmount(order.getTotalAmount());
					vo.setActualAmount(order.getActualAmount());
					vo.setStatus(order.getStatus());
					vo.setStatusName(getStatusName(order.getStatus()));
					vo.setCreateTime(order.getCreateTime());


					// 从订单商品明细中获取商品信息
					TOrderItem orderItem = orderItemService.lambdaQuery()
							.eq(TOrderItem::getOrderNo, order.getOrderNo())
							.one();
					if (orderItem != null) {
						vo.setProductImage(orderItem.getProductImage());
						vo.setProductName(orderItem.getProductName());
						vo.setQuantity(orderItem.getQuantity());
					}

					return vo;
				})
				.collect(Collectors.toList());

		// 5. 构建分页结果
		PageResult<OrderListItemVO> result = new PageResult<>();
		result.setTotal(orderPage.getTotal());
		result.setPageNum((int) orderPage.getCurrent());
		result.setPageSize((int) orderPage.getSize());
		result.setList(voList);

		return result;
	}

	/**
	 * 获取订单类型名称
	 */
	private String getOrderTypeName(Integer orderType) {
		if (orderType == null) {
			return "普通订单";
		}
		switch (orderType) {
			case 1:
				return "普通订单";
			case 2:
				return "秒杀订单";
			case 3:
				return "团购订单";
			default:
				return "普通订单";
		}
	}

	/**
	 * 获取订单状态名称
	 */
	private String getStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		switch (status) {
			case 0:
				return "待支付";
			case 1:
				return "已支付";
			case 2:
				return "已发货";
			case 3:
				return "已完成";
			case 4:
				return "已取消";
			default:
				return "未知";
		}
	}
}
