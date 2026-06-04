package com.haalan.gateway.filter;

import com.haalan.common.domain.ResultCode;
import com.haalan.common.exception.UnauthorizedException;
import com.haalan.common.utils.CollUtils;
import com.haalan.gateway.config.AuthProperties;
import com.haalan.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class AuthGlobalFilter implements GlobalFilter, Ordered {

	private final JwtTool jwtTool;

	private final AuthProperties authProperties;

	private final StringRedisTemplate stringRedisTemplate;

	private final AntPathMatcher antPathMatcher = new AntPathMatcher();

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 1.获取Request
		ServerHttpRequest request = exchange.getRequest();
		String requestPath = request.getPath().toString();
		// 2.判断是否不需要拦截
		if (isExclude(requestPath)) {
			// 无需拦截，直接放行
			return chain.filter(exchange);
		}

		// 3.获取请求头中的token
		String token = null;
		List<String> headers = request.getHeaders().get("Authorization");
		if (!CollUtils.isEmpty(headers)) {
			token = headers.get(0);
		}

//  先判断 token 是否为 null
		if (token == null) {
			log.warn("请求未携带token");
			ServerHttpResponse response = exchange.getResponse();
			response.setRawStatusCode(ResultCode.UNAUTHORIZED);
			return response.setComplete();
		}

//  再判断格式是否正确
		if (!token.startsWith("Bearer ")) {
			log.warn("请求token格式错误");
			ServerHttpResponse response = exchange.getResponse();
			response.setRawStatusCode(ResultCode.UNAUTHORIZED);
			return response.setComplete();
		}

//  去掉 Bearer 前缀
		token = token.substring(7);
		if (token == null) {
			log.warn("请求未携带token");
			ServerHttpResponse response = exchange.getResponse();
			response.setRawStatusCode(ResultCode.UNAUTHORIZED);
			return response.setComplete();
		}

		// 4.校验并解析token
		final JwtTool.TokenPayload tokenPayload;
		try {

			tokenPayload = jwtTool.parseToken(token);
			log.info("用户信息：{}", tokenPayload);

			Long userId = tokenPayload.getUserId();
			Integer tokenVersion = tokenPayload.getVersion();

			String redisKey = "user:token:version:" + userId;
			String cachedVersion = stringRedisTemplate.opsForValue().get(redisKey);

			if (cachedVersion != null) {
				Integer redisVersion = Integer.valueOf(cachedVersion);
				if (tokenVersion < redisVersion) {
					log.warn("用户{}的Token版本{}已过期，当前最新版本: {}", userId, tokenVersion, redisVersion);
					ServerHttpResponse response = exchange.getResponse();
					response.setRawStatusCode(ResultCode.UNAUTHORIZED);
					return response.setComplete();
				}
				// 检查管理员权限：访问 api/admin/ 路径需要 token_version = 0
				if (isAdminPath(requestPath) && tokenVersion != 0) {
					log.warn("用户{} 尝试访问管理员路径 {} 但权限不足，token_version={}",
							userId, requestPath, tokenVersion);
					ServerHttpResponse response = exchange.getResponse();
					response.setRawStatusCode(ResultCode.FORBIDDEN); // 使用403状态码更好区分
					return response.setComplete();
				}
			} else {
				stringRedisTemplate.opsForValue().set(redisKey, tokenVersion.toString(), 2, TimeUnit.HOURS);
			}

			log.info("用户{} Token校验通过，版本: {}", userId, tokenVersion);

		} catch (UnauthorizedException e) {
			ServerHttpResponse response = exchange.getResponse();
			response.setRawStatusCode(ResultCode.UNAUTHORIZED);
			return response.setComplete();
		}

		// 5.传递用户信息（userId + version）
		exchange.mutate()
				.request(builder -> builder
						.header("user-info", tokenPayload.getUserId().toString())
						.header("token-version", tokenPayload.getVersion().toString()))
				.build();

		return chain.filter(exchange);
	}

	private boolean isAdminPath(String path) {
		return path.startsWith("/api/admin");
	}

	private boolean isExclude(String antPath) {
		for (String pathPattern : authProperties.getExcludePaths()) {
			if (antPathMatcher.match(pathPattern, antPath)) {
				return true;
			}
		}
		return false;
	}


	@Override
	public int getOrder() {
		return 0;
	}
}