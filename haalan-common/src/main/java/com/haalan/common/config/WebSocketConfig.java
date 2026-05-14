package com.haalan.common.config;

import com.haalan.common.websocket.CommonWebSocketServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类（通用）
 */
@Configuration
public class WebSocketConfig {

	/**
	 * 注入ServerEndpointExporter，
	 * 这个bean会自动注册使用了@ServerEndpoint注解声明的Websocket endpoint
	 */
	@Bean
	public ServerEndpointExporter serverEndpointExporter() {
		return new ServerEndpointExporter();
	}

	/**
	 * 注册WebSocket端点
	 */
	@Bean
	public CommonWebSocketServer commonWebSocketServer() {
		return new CommonWebSocketServer();
	}
}
