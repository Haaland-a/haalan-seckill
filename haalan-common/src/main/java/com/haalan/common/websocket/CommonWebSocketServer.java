package com.haalan.common.websocket;

import cn.hutool.json.JSONUtil;
import com.haalan.common.domain.vo.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务端点（通用）
 * 路径格式: /ws/{service}/{userId}
 * 例如: /ws/order/1001, /ws/seckill/1001, /ws/item/1001
 */
@Slf4j
@ServerEndpoint("/ws/{service}/{userId}")
public class CommonWebSocketServer {

	/**
	 * 存储所有在线用户的WebSocket会话
	 * Key: service_userId (例如: order_1001), Value: Session
	 */
	private static final ConcurrentHashMap<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

	/**
	 * 连接建立成功调用的方法
	 */
	@OnOpen
	public void onOpen(Session session, @PathParam("service") String service, @PathParam("userId") Long userId) {
		String key = buildKey(service, userId);
		SESSION_MAP.put(key, session);
		log.info("[{}] 用户[{}]建立WebSocket连接，当前在线人数: {}", service, userId, SESSION_MAP.size());

		// 发送欢迎消息
		WebSocketMessage welcomeMsg = WebSocketMessage.builder()
				.messageType("SYSTEM_NOTICE")
				.title("连接成功")
				.content("您已成功连接到" + getServiceName(service) + "消息推送服务")
				.timestamp(LocalDateTime.now())
				.build();
		sendMessage(session, welcomeMsg);
	}

	/**
	 * 连接关闭调用的方法
	 */
	@OnClose
	public void onClose(Session session, @PathParam("service") String service, @PathParam("userId") Long userId) {
		String key = buildKey(service, userId);
		SESSION_MAP.remove(key);
		log.info("[{}] 用户[{}]断开WebSocket连接，当前在线人数: {}", service, userId, SESSION_MAP.size());
	}

	/**
	 * 收到客户端消息后调用的方法
	 */
	@OnMessage
	public void onMessage(String message, Session session,
						  @PathParam("service") String service,
						  @PathParam("userId") Long userId) {
		log.info("[{}] 收到用户[{}]的消息: {}", service, userId, message);

		// 可以处理客户端发来的心跳消息等
		try {
			session.getBasicRemote().sendText("pong");
		} catch (IOException e) {
			log.error("发送响应失败", e);
		}
	}

	/**
	 * 发生错误时调用
	 */
	@OnError
	public void onError(Session session, Throwable error,
						@PathParam("service") String service,
						@PathParam("userId") Long userId) {
		log.error("[{}] 用户[{}]WebSocket发生错误", service, userId, error);
		String key = buildKey(service, userId);
		SESSION_MAP.remove(key);
	}

	/**
	 * 向指定服务的指定用户发送消息
	 *
	 * @param service 服务名称 (order/seckill/item)
	 * @param userId  用户ID
	 * @param message 消息对象
	 */
	public static void sendMessageToUser(String service, Long userId, WebSocketMessage message) {
		String key = buildKey(service, userId);
		Session session = SESSION_MAP.get(key);
		if (session != null && session.isOpen()) {
			sendMessage(session, message);
			log.debug("[{}] 向用户[{}]发送消息成功", service, userId);
		} else {
			log.warn("[{}] 用户[{}]不在线或连接已关闭，消息发送失败", service, userId);
		}
	}

	/**
	 * 向指定服务的所有在线用户广播消息
	 *
	 * @param service 服务名称
	 * @param message 消息对象
	 */
	public static void broadcastToService(String service, WebSocketMessage message) {
		log.info("[{}] 开始广播消息", service);
		for (String key : SESSION_MAP.keySet()) {
			if (key.startsWith(service + "_")) {
				Session session = SESSION_MAP.get(key);
				if (session != null && session.isOpen()) {
					sendMessage(session, message);
				}
			}
		}
	}

	/**
	 * 向所有在线用户广播消息
	 *
	 * @param message 消息对象
	 */
	public static void broadcastAll(WebSocketMessage message) {
		log.info("开始全局广播消息，当前在线人数: {}", SESSION_MAP.size());
		for (Session session : SESSION_MAP.values()) {
			if (session.isOpen()) {
				sendMessage(session, message);
			}
		}
	}

	/**
	 * 发送消息的具体实现
	 */
	private static void sendMessage(Session session, WebSocketMessage message) {
		try {
			String jsonMessage = JSONUtil.toJsonStr(message);
			session.getBasicRemote().sendText(jsonMessage);
		} catch (IOException e) {
			log.error("发送消息失败", e);
		}
	}

	/**
	 * 获取指定服务的在线人数
	 */
	public static int getOnlineCount(String service) {
		return (int) SESSION_MAP.keySet().stream()
				.filter(key -> key.startsWith(service + "_"))
				.count();
	}

	/**
	 * 获取总在线人数
	 */
	public static int getTotalOnlineCount() {
		return SESSION_MAP.size();
	}

	/**
	 * 检查用户是否在线
	 */
	public static boolean isUserOnline(String service, Long userId) {
		String key = buildKey(service, userId);
		Session session = SESSION_MAP.get(key);
		return session != null && session.isOpen();
	}

	/**
	 * 构建Session Map的Key
	 */
	private static String buildKey(String service, Long userId) {
		return service + "_" + userId;
	}

	/**
	 * 获取服务中文名称
	 */
	private static String getServiceName(String service) {
		switch (service.toLowerCase()) {
			case "order":
				return "订单";
			case "seckill":
				return "秒杀";
			case "item":
				return "商品";
			default:
				return service;
		}
	}
}
