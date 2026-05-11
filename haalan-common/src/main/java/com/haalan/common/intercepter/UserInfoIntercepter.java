package com.haalan.common.intercepter;

import cn.hutool.core.util.StrUtil;
import com.haalan.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class UserInfoIntercepter implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String userInfo = request.getHeader("user-info");
		if (StrUtil.isNotBlank(userInfo)) {
			UserContext.setUser(Long.valueOf(userInfo));
			log.debug("用户信息已存入ThreadLocal: {}", userInfo);
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		UserContext.removeUser();
	}
}
