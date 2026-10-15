package com.haalan.gateway.filter;

import com.haalan.gateway.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * <p>
 * 内部接口（/inner/**）访问守卫
 * </p>
 *
 * <p>
 * item-service 的内部接口全部挂在 {@code /api/product/inner/**}，而网关给 item-service
 * 注册的路由前缀是 {@code /api/product/**}，两者是包含关系。结果是任何登录用户（甚至不需要管理员）
 * 都能从公网直接调用：
 * </p>
 * <pre>
 *   GET /api/product/inner/addStock?skuId=1&amp;stock=1000   → 200 库存被改写
 *   GET /api/product/inner/deductStock?skuId=1&amp;num=100   → 200 库存被扣减
 * </pre>
 * <p>
 * 这些接口只应该被微服务之间的 OpenFeign 调用（走服务名直连，不经过网关），
 * 因此这里在网关最前面统一把命中 {@code haalan.auth.blockedPaths} 的请求挡掉。
 * </p>
 * <p>
 * order 必须小于 {@link AuthGlobalFilter}（0），否则未登录请求会先被 401 拦下，
 * 从而「未登录访问内部接口返回 401、已登录返回 404」这种不一致的响应会泄露接口是否存在。
 * </p>
 *
 * @author Haaland
 * @date 2026/10/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class InnerApiGuardFilter implements GlobalFilter, Ordered {

	private final AuthProperties authProperties;

	private final AntPathMatcher antPathMatcher = new AntPathMatcher();

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getPath().toString();

		if (isBlocked(path)) {
			// 记 warn 而不是 info：出现这条日志说明有人在探测内部接口，值得关注
			log.warn("拦截内部接口访问: {} {}", request.getMethod(), path);
			ServerHttpResponse response = exchange.getResponse();
			// 直接 404：不暴露「该接口存在但被禁止」这一信息
			response.setRawStatusCode(404);
			return response.setComplete();
		}

		return chain.filter(exchange);
	}

	private boolean isBlocked(String path) {
		List<String> blockedPaths = authProperties.getBlockedPaths();
		if (blockedPaths == null || blockedPaths.isEmpty()) {
			return false;
		}
		// 兜底：即使配置被改坏，也保证任何含 /inner/ 的路径都进不来
		if (path.contains("/inner/")) {
			return true;
		}
		for (String pattern : blockedPaths) {
			if (antPathMatcher.match(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getOrder() {
		// 必须早于 AuthGlobalFilter（0）
		return -100;
	}
}
