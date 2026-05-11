package com.haalan.common.domain;

import com.haalan.common.exception.CommonException;
import lombok.Data;


@Data
public class R<T> {
	private Integer code;      // 状态码: 200-成功 其他-失败
	private String message;    // 提示信息
	private T data;           // 响应数据
	private Long timestamp;    // 时间戳


	public static <T> R<T> success(T data) {
		return new R<>(200, "success", data, System.currentTimeMillis());
	}

	public static <T> R<T> success(String msg, T data) {
		return new R<>(200, msg, data, System.currentTimeMillis());
	}

	public static <T> R<T> success(String msg) {
		return new R<>(200, msg, null, System.currentTimeMillis());
	}

	public static <T> R<T> success(int code, String msg, T data) {
		return new R<>(code, msg, data, System.currentTimeMillis());
	}

	public static <T> R<T> error(String msg) {
		return new R<>(500, msg, null, System.currentTimeMillis());
	}

	public static <T> R<T> error(int code, String msg) {
		return new R<>(code, msg, null, System.currentTimeMillis());
	}


	public static <T> R<T> error(CommonException e) {
		return new R<>(e.getCode(), e.getMessage(), null, System.currentTimeMillis());
	}

	public R(int code, String message, Object data, long timestamp) {
		this.code = code;
		this.message = message;
		this.data = (T) data;
		this.timestamp = timestamp;
	}


	public boolean success() {
		return code == 200;
	}
}
