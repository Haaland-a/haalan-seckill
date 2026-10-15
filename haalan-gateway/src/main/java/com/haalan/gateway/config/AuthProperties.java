package com.haalan.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "haalan.auth")
public class AuthProperties {
	private List<String> includePaths;
	private List<String> excludePaths;
	/**
	 * 一律禁止从网关外部访问的路径（Ant 风格）。
	 * 用于拦掉 item-service 挂在 /api/product/inner/** 下的内部接口，
	 * 这些接口只应被微服务间的 OpenFeign 直连调用。
	 */
	private List<String> blockedPaths;
}
