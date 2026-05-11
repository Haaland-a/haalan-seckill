package com.haalan.user.utils;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.haalan.common.exception.UnauthorizedException;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

/**
 * JWT工具类 - 支持Token版本号校验
 */
@Component
public class JwtTool {
	private final JWTSigner jwtSigner;

	public JwtTool(KeyPair keyPair) {
		this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
	}

	/**
	 * Token解析结果
	 */
	@Data
	public static class TokenPayload {
		private Long userId;
		private Integer version;
	}

	/**
	 * 创建JWT Token（包含版本号）
	 *
	 * @param userId  用户ID
	 * @param version Token版本号
	 * @param ttl     Token有效期
	 * @return JWT Token字符串
	 */
	public String createToken(Long userId, Integer version, Duration ttl) {
		return JWT.create()
				.setPayload("user", userId)
				.setPayload("version", version)
				.setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
				.setSigner(jwtSigner)
				.sign();
	}

	/**
	 * 解析JWT Token（返回userId和version）
	 *
	 * @param token JWT Token字符串
	 * @return TokenPayload（包含userId和version）
	 * @throws UnauthorizedException Token无效时抛出异常
	 */
	public TokenPayload parseToken(String token) {
		if (token == null) {
			throw new UnauthorizedException("未登录");
		}

		JWT jwt;
		try {
			jwt = JWT.of(token).setSigner(jwtSigner);
		} catch (Exception e) {
			throw new UnauthorizedException("无效的token", e);
		}

		if (!jwt.verify()) {
			throw new UnauthorizedException("无效的token");
		}

		try {
			JWTValidator.of(jwt).validateDate();
		} catch (ValidateException e) {
			throw new UnauthorizedException("token已经过期");
		}

		Object userPayload = jwt.getPayload("user");
		Object versionPayload = jwt.getPayload("version");

		if (userPayload == null || versionPayload == null) {
			throw new UnauthorizedException("无效的token");
		}

		TokenPayload payload = new TokenPayload();
		try {
			payload.setUserId(Long.valueOf(userPayload.toString()));
			payload.setVersion(Integer.valueOf(versionPayload.toString()));
		} catch (RuntimeException e) {
			throw new UnauthorizedException("无效的token");
		}

		return payload;
	}

	/**
	 * 兼容旧方法：仅获取userId（用于不需要version校验的场景）
	 */
	public Long parseTokenToUserId(String token) {
		TokenPayload payload = parseToken(token);
		return payload.getUserId();
	}
}