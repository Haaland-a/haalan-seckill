package com.haalan.common.config;

import com.haalan.common.domain.R;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.exception.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.annotation.PostConstruct;

//全局异常处理
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@PostConstruct
	public void init() {
		log.info("GlobalExceptionHandler 已初始化");
	}

	@ExceptionHandler(BizIllegalException.class)
	public R<Void> handleBizIllegalException(BizIllegalException e) {
		log.warn("业务异常: {}", e.getMessage());
		return R.error(e.getCode(), e.getMessage());
	}

	@ExceptionHandler(CommonException.class)
	public R<Void> handleCommonException(CommonException e) {
		log.warn("系统异常: {}", e.getMessage());
		return R.error(e);
	}

	@ExceptionHandler(Exception.class)
	public R<Void> handleException(Exception e) {
		log.error("系统异常", e);
		return R.error(500, "服务器内部错误");
	}

	@ExceptionHandler(DuplicateKeyException.class)
	public R<Void> handleDuplicateKeyException(DuplicateKeyException e) {
		log.error("数据重复异常: {}", e.getMessage());

		String message = e.getMessage();
		if (message.contains("uk_phone")) {
			return R.error("手机号已被注册");
		}
		if (message.contains("uk_username")) {
			return R.error("用户名已存在");
		}
		if (message.contains("uk_email")) {
			return R.error("邮箱已被使用");
		}

		return R.error("数据重复，请检查后重试");
	}
}