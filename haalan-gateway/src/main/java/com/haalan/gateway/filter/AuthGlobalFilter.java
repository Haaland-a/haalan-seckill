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

			// 管理员判定必须放在 Token 版本缓存命中与否的分支之外。
			// 原来这段判定被包在 if (cachedVersion != null) 里，Redis 里没有
			// user:token:version:{userId} 时（冷缓存、缓存被清、Redis 重启）会整段跳过，
			// 任何登录用户都能访问 /api/admin/**。
			//
			// 判定依据也从 tokenVersion == 0 换成 Token 里的 admin claim：
			// 原来那个写法把 token_version 当成角色用，而 token_version 是「改密/退出就 +1」的
			// 踢人计数器 —— 管理员改一次密码就再也进不去后台了。
			if (isAdminPath(requestPath) && !Boolean.TRUE.equals(tokenPayload.getAdmin())) {
				log.warn("用户{} 尝试访问管理员路径 {} 但权限不足", userId, requestPath);
				ServerHttpResponse response = exchange.getResponse();
				response.setRawStatusCode(ResultCode.FORBIDDEN); // 使用403状态码更好区分
				return response.setComplete();
			}

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
			} else {
				stringRedisTemplate.opsForValue().set(redisKey, tokenVersion.toString(), 2, TimeUnit.HOURS);
			}

			log.info("用户{} Token校验通过，版本: {}, 管理员: {}",
					userId, tokenVersion, Boolean.TRUE.equals(tokenPayload.getAdmin()));

		} catch (UnauthorizedException e) {
			ServerHttpResponse response = exchange.getResponse();
			response.setRawStatusCode(ResultCode.UNAUTHORIZED);
			return response.setComplete();
		}

		// 5.传递用户信息（userId + version）
		// 注意：mutate() 返回的是一个新的 exchange（请求头被改写的那份），
		// 必须把它赋回给 exchange 再交给 chain，否则下游微服务收到的请求里
		// 没有 user-info / token-version 头，UserContext.getUser() 会永远读不到用户。
		ServerWebExchange mutatedExchange = exchange.mutate()
				.request(builder -> builder
						.header("user-info", tokenPayload.getUserId().toString())
						.header("token-version", tokenPayload.getVersion().toString()))
				.build();

		return chain.filter(mutatedExchange);
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