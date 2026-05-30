package com.haalan.user.aop;

import com.haalan.user.domain.po.UserLoginLog;
import com.haalan.user.service.UserLoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 用户登录日志AOP切面
 * 用于拦截登录方法，记录用户登录信息到数据库
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoginLogAspect {

	private final UserLoginLogService userLoginLogService;

	/**
	 * 定义切入点：拦截TUserService的login方法
	 */
	@Pointcut("@annotation(com.haalan.user.annotation.Login)")
	public void loginPointcut() {
	}

	/**
	 * 登录成功后的处理
	 *
	 * @param joinPoint 连接点
	 * @param result    返回结果
	 */
	@AfterReturning(pointcut = "loginPointcut()", returning = "result")
	public void doAfterReturning(JoinPoint joinPoint, Object result) {
		try {
			UserLoginLog loginLog = buildLoginLog(joinPoint, result, true, null);
			userLoginLogService.saveLoginLog(loginLog);
		} catch (Exception e) {
			log.error("记录登录成功日志时发生异常", e);
		}
	}

	/**
	 * 登录失败后的处理
	 *
	 * @param joinPoint 连接点
	 * @param exception 异常信息
	 */
	@AfterThrowing(pointcut = "loginPointcut()", throwing = "exception")
	public void doAfterThrowing(JoinPoint joinPoint, Throwable exception) {
		try {
			UserLoginLog loginLog = buildLoginLog(joinPoint, null, false, exception.getMessage());
			userLoginLogService.saveLoginLog(loginLog);
		} catch (Exception e) {
			log.error("记录登录失败日志时发生异常", e);
		}
	}

	/**
	 * 构建登录日志对象
	 *
	 * @param joinPoint  连接点
	 * @param result     返回结果
	 * @param success    是否成功
	 * @param failReason 失败原因
	 * @return 登录日志对象
	 */
	private UserLoginLog buildLoginLog(JoinPoint joinPoint, Object result, boolean success, String failReason) {
		UserLoginLog loginLog = new UserLoginLog();


		// 获取请求信息
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attributes != null) {
			HttpServletRequest request = attributes.getRequest();
			// 获取IP地址
			String ip = getClientIp(request);
			loginLog.setLoginIp(ip);
		}

		// 获取方法参数（LoginDTO）
		Object[] args = joinPoint.getArgs();
		if (args != null && args.length > 0) {
			// 从LoginDTO中获取用户名
			for (Object arg : args) {
				if (arg != null && arg.getClass().getSimpleName().equals("LoginDTO")) {
					try {
						Method getUsernameMethod = arg.getClass().getMethod("getUsername");
						String username = (String) getUsernameMethod.invoke(arg);
						loginLog.setUsername(username);
					} catch (Exception e) {
						log.warn("获取用户名失败", e);
					}
					break;
				}
			}
		}

// 如果登录成功，从返回结果中获取userId
		if (success && result != null) {
			try {
				// 方式1：如果返回的是 R 对象
				if (result.getClass().getSimpleName().equals("R")) {
					// 获取 R 对象中的 data
					Method getDataMethod = result.getClass().getMethod("getData");
					Object data = getDataMethod.invoke(result);

					// 从 data (LoginVO) 中获取 userId
					if (data != null) {
						Method getUserIdMethod = data.getClass().getMethod("getUserId");
						Long userId = (Long) getUserIdMethod.invoke(data);
						loginLog.setUserId(userId);
					}
				} else {
					// 方式2：直接获取（如果返回的就是 LoginVO）
					Method getUserIdMethod = result.getClass().getMethod("getUserId");
					Long userId = (Long) getUserIdMethod.invoke(result);
					loginLog.setUserId(userId);
				}
			} catch (Exception e) {
				log.warn("从返回结果中获取userId失败", e);
			}
		}

		// 设置其他字段
		loginLog.setLoginTime(LocalDateTime.now());
		loginLog.setLoginResult(success ? 1 : 0);
		loginLog.setFailReason(failReason);

		return loginLog;
	}

	/**
	 * 获取客户端真实IP地址
	 *
	 * @param request HTTP请求
	 * @return IP地址
	 */
	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Real-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		// 对于通过多个代理的情况，第一个IP为客户端真实IP
		if (ip != null && ip.contains(",")) {
			ip = ip.split(",")[0].trim();
		}
		return ip;
	}
}
