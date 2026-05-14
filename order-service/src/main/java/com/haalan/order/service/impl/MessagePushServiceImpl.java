package com.haalan.order.service.impl;

import com.haalan.common.domain.vo.WebSocketMessage;
import com.haalan.common.websocket.CommonWebSocketServer;
import com.haalan.order.service.IMessagePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息推送服务实现（订单服务）
 */
@Slf4j
@Service
public class MessagePushServiceImpl implements IMessagePushService {

	private static final String SERVICE_NAME = "order";

	/**
	 * 推送订单状态变更消息
	 *
	 * @param userId     用户ID
	 * @param orderNo    订单号
	 * @param status     订单状态
	 * @param statusName 状态名称
	 */
	public void pushOrderStatusChange(Long userId, String orderNo, Integer status, String statusName) {
		Map<String, Object> data = new HashMap<>();
		data.put("orderNo", orderNo);
		data.put("status", status);
		data.put("statusName", statusName);

		WebSocketMessage message = WebSocketMessage.builder()
				.messageType("ORDER_STATUS_CHANGE")
				.title("订单状态变更")
				.content("您的订单 " + orderNo + " 状态已变更为：" + statusName)
				.data(data)
				.timestamp(LocalDateTime.now())
				.build();

		CommonWebSocketServer.sendMessageToUser(SERVICE_NAME, userId, message);
		log.info("向用户[{}]推送订单状态变更消息: orderNo={}, status={}", userId, orderNo, statusName);
	}

	/**
	 * 推送订单取消消息
	 *
	 * @param userId   用户ID
	 * @param response 取消订单响应
	 */
	@Override
	public void pushOrderCancel(Long userId, com.haalan.order.domain.vo.CancelOrderResponseVO response) {
		pushOrderStatusChange(userId, response.getOrderNo(), response.getStatus(), response.getStatusName());
	}

	/**
	 * 推送支付成功消息
	 *
	 * @param userId  用户ID
	 * @param orderNo 订单号
	 */
	public void pushPaymentSuccess(Long userId, String orderNo) {
		Map<String, Object> data = new HashMap<>();
		data.put("orderNo", orderNo);

		WebSocketMessage message = WebSocketMessage.builder()
				.messageType("PAYMENT_SUCCESS")
				.title("支付成功")
				.content("您的订单 " + orderNo + " 已支付成功")
				.data(data)
				.timestamp(LocalDateTime.now())
				.build();

		CommonWebSocketServer.sendMessageToUser(SERVICE_NAME, userId, message);
		log.info("向用户[{}]推送支付成功消息: orderNo={}", userId, orderNo);
	}

	/**
	 * 推送系统通知
	 *
	 * @param userId  用户ID
	 * @param title   标题
	 * @param content 内容
	 */
	public void pushSystemNotice(Long userId, String title, String content) {
		WebSocketMessage message = WebSocketMessage.builder()
				.messageType("SYSTEM_NOTICE")
				.title(title)
				.content(content)
				.timestamp(LocalDateTime.now())
				.build();

		CommonWebSocketServer.sendMessageToUser(SERVICE_NAME, userId, message);
		log.info("向用户[{}]推送系统通知: {}", userId, title);
	}

	/**
	 * 广播系统通知（发送给订单服务的所有在线用户）
	 *
	 * @param title   标题
	 * @param content 内容
	 */
	public void broadcastSystemNotice(String title, String content) {
		WebSocketMessage message = WebSocketMessage.builder()
				.messageType("SYSTEM_NOTICE")
				.title(title)
				.content(content)
				.timestamp(LocalDateTime.now())
				.build();

		CommonWebSocketServer.broadcastToService(SERVICE_NAME, message);
		log.info("广播系统通知: {}", title);
	}
}
