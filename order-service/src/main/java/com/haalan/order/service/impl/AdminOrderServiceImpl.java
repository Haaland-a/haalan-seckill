package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haalan.common.domain.PageResult;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.domain.dto.AdminOrderQueryDTO;
import com.haalan.order.domain.dto.OrderStatusUpdateDTO;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.AdminOrderListVO;
import com.haalan.order.mapper.TOrderItemMapper;
import com.haalan.order.mapper.TOrderMapper;
import com.haalan.order.service.IAdminOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements IAdminOrderService {

	private final TOrderMapper orderMapper;
	private final TOrderItemMapper orderItemMapper;

	@Override
	public PageResult<AdminOrderListVO> listOrders(AdminOrderQueryDTO query) {
		LambdaQueryWrapper<TOrder> wrapper = new LambdaQueryWrapper<>();
		if (StringUtils.hasText(query.getOrderNo())) {
			wrapper.like(TOrder::getOrderNo, query.getOrderNo());
		}
		if (query.getStatus() != null) {
			wrapper.eq(TOrder::getStatus, query.getStatus());
		}
		if (query.getOrderType() != null) {
			wrapper.eq(TOrder::getOrderType, query.getOrderType());
		}
		if (StringUtils.hasText(query.getStartDate())) {
			wrapper.ge(TOrder::getCreateTime, query.getStartDate() + " 00:00:00");
		}
		if (StringUtils.hasText(query.getEndDate())) {
			wrapper.le(TOrder::getCreateTime, query.getEndDate() + " 23:59:59");
		}
		wrapper.orderByDesc(TOrder::getCreateTime);

		Page<TOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
		Page<TOrder> result = orderMapper.selectPage(page, wrapper);

		List<AdminOrderListVO> voList = result.getRecords().stream()
				.map(this::convertNormalOrder)
				.collect(Collectors.toList());

		PageResult<AdminOrderListVO> pageResult = new PageResult<>();
		pageResult.setTotal(result.getTotal());
		pageResult.setPageNum((int) result.getCurrent());
		pageResult.setPageSize((int) result.getSize());
		pageResult.setList(voList);
		return pageResult;
	}

	@Override
	public Map<String, Object> getOrderDetail(String orderNo) {
		TOrder order = orderMapper.selectOne(
				new LambdaQueryWrapper<TOrder>().eq(TOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}
		List<TOrderItem> items = orderItemMapper.selectList(
				new LambdaQueryWrapper<TOrderItem>().eq(TOrderItem::getOrderNo, orderNo));

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("order", convertNormalOrderDetail(order));
		detail.put("items", items);
		return detail;
	}

	@Override
	public void updateOrderStatus(String orderNo, OrderStatusUpdateDTO dto) {
		TOrder order = orderMapper.selectOne(
				new LambdaQueryWrapper<TOrder>().eq(TOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}
		// 简单状态流转校验
		if (dto.getStatus() == 2 && order.getStatus() != 1) {
			throw new BizIllegalException("只有已支付的订单才能发货");
		}
		if (dto.getStatus() == 3 && order.getStatus() != 2) {
			throw new BizIllegalException("只有已发货的订单才能完成");
		}
		order.setStatus(dto.getStatus());
		orderMapper.updateById(order);
		log.info("订单 {} 状态已更新为 {}", orderNo, dto.getStatus());
	}

	@Override
	public PageResult<AdminOrderListVO> listSeckillOrders(AdminOrderQueryDTO query) {
		String startDate = StringUtils.hasText(query.getStartDate()) ? query.getStartDate() + " 00:00:00" : null;
		String endDate = StringUtils.hasText(query.getEndDate()) ? query.getEndDate() + " 23:59:59" : null;

		long total = orderMapper.countSeckillOrders(query.getOrderNo(), query.getStatus(), startDate, endDate);
		int offset = (query.getPageNum() - 1) * query.getPageSize();
		List<TSeckillOrder> list = orderMapper.pageSeckillOrders(
				query.getOrderNo(), query.getStatus(), startDate, endDate, offset, query.getPageSize());

		List<AdminOrderListVO> voList = list.stream()
				.map(this::convertSeckillOrder)
				.collect(Collectors.toList());

		PageResult<AdminOrderListVO> pageResult = new PageResult<>();
		pageResult.setTotal(total);
		pageResult.setPageNum(query.getPageNum());
		pageResult.setPageSize(query.getPageSize());
		pageResult.setList(voList);
		return pageResult;
	}

	@Override
	public Map<String, Object> getSeckillOrderDetail(String orderNo) {
		// 显式查两张分表，绕过动态表名拦截器
		TSeckillOrder order = orderMapper.getSeckillOrderByNo(orderNo);
		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("order", order);
		return detail;
	}

	private AdminOrderListVO convertNormalOrder(TOrder order) {
		AdminOrderListVO vo = new AdminOrderListVO();
		vo.setId(order.getId());
		vo.setOrderNo(order.getOrderNo());
		vo.setUserId(order.getUserId());
		vo.setTotalAmount(order.getTotalAmount());
		vo.setActualAmount(order.getActualAmount());
		vo.setOrderType(order.getOrderType());
		vo.setOrderTypeName(getOrderTypeName(order.getOrderType()));
		vo.setStatus(order.getStatus());
		vo.setStatusName(getOrderStatusName(order.getStatus()));
		vo.setCreateTime(order.getCreateTime());
		return vo;
	}

	private AdminOrderListVO convertSeckillOrder(TSeckillOrder order) {
		AdminOrderListVO vo = new AdminOrderListVO();
		vo.setId(order.getId());
		vo.setOrderNo(order.getOrderNo());
		vo.setUserId(order.getUserId());
		vo.setTotalAmount(order.getTotalAmount());
		vo.setActualAmount(order.getTotalAmount());
		vo.setOrderType(2);
		vo.setOrderTypeName("秒杀订单");
		vo.setStatus(order.getStatus());
		vo.setStatusName(getSeckillOrderStatusName(order.getStatus()));
		vo.setCreateTime(order.getCreateTime());
		return vo;
	}

	private Map<String, Object> convertNormalOrderDetail(TOrder order) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("id", order.getId());
		detail.put("orderNo", order.getOrderNo());
		detail.put("userId", order.getUserId());
		detail.put("totalAmount", order.getTotalAmount());
		detail.put("discountAmount", order.getDiscountAmount());
		detail.put("actualAmount", order.getActualAmount());
		detail.put("orderType", order.getOrderType());
		detail.put("orderTypeName", getOrderTypeName(order.getOrderType()));
		detail.put("status", order.getStatus());
		detail.put("statusName", getOrderStatusName(order.getStatus()));
		detail.put("receiverInfo", order.getReceiverInfo());
		detail.put("remark", order.getRemark());
		detail.put("createTime", order.getCreateTime());
		return detail;
	}

	private String getOrderTypeName(Integer type) {
		if (type == null) return "未知";
		return switch (type) {
			case 1 -> "普通订单";
			case 2 -> "秒杀订单";
			case 3 -> "团购订单";
			default -> "未知";
		};
	}

	private String getOrderStatusName(Integer status) {
		if (status == null) return "未知";
		return switch (status) {
			case 0 -> "待支付";
			case 1 -> "已支付";
			case 2 -> "已发货";
			case 3 -> "已完成";
			case 4 -> "已取消";
			default -> "未知";
		};
	}

	private String getSeckillOrderStatusName(Integer status) {
		if (status == null) return "未知";
		return switch (status) {
			case 0 -> "待支付";
			case 1 -> "已支付";
			case 2 -> "已取消";
			case 3 -> "已超时";
			default -> "未知";
		};
	}
}
