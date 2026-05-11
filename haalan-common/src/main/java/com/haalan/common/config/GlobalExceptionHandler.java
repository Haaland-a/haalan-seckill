package com.haalan.common.config;

import com.haalan.common.domain.R;
import com.haalan.common.exception.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
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

	@ExceptionHandler(Exception.class)
	public R<Void> handleException(Exception e) {
		log.error("系统异常", e);
		return R.error(500, "服务器内部错误");
	}
}