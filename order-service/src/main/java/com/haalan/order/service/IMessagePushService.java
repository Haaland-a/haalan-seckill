package com.haalan.order.service;

import com.haalan.order.domain.vo.CancelOrderResponseVO;

/**
 * 消息推送服务接口（订单服务）
 */
public interface IMessagePushService {

	/**
	 * 推送订单状态变更消息
	 *
	 * @param userId     用户ID
	 * @param orderNo    订单号
	 * @param status     订单状态
	 * @param statusName 状态名称
	 */
	void pushOrderStatusChange(Long userId, String orderNo, Integer status, String statusName);

	/**
	 * 推送订单取消消息
	 *
	 * @param userId   用户ID
	 * @param response 取消订单响应
	 */
	void pushOrderCancel(Long userId, CancelOrderResponseVO response);

	/**
	 * 推送支付成功消息
	 *
	 * @param userId  用户ID
	 * @param orderNo 订单号
	 */
	void pushPaymentSuccess(Long userId, String orderNo);

	/**
	 * 推送系统通知
	 *
	 * @param userId  用户ID
	 * @param title   标题
	 * @param content 内容
	 */
	void pushSystemNotice(Long userId, String title, String content);

	/**
	 * 广播系统通知（发送给订单服务的所有在线用户）
	 *
	 * @param title   标题
	 * @param content 内容
	 */
	void broadcastSystemNotice(String title, String content);
}
