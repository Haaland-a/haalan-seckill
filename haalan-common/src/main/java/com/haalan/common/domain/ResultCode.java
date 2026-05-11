package com.haalan.common.domain;

public class ResultCode {
	public static final int SUCCESS = 200;
	public static final int BAD_REQUEST = 400;
	public static final int UNAUTHORIZED = 401;
	public static final int FORBIDDEN = 403;
	public static final int NOT_FOUND = 404;
	public static final int CONFLICT = 409;
	public static final int SERVER_ERROR = 500;

	// 秒杀业务错误码
	public static final int SECKILL_NOT_START = 1001;
	public static final int SECKILL_ENDED = 1002;
	public static final int STOCK_NOT_ENOUGH = 1003;
	public static final int REPEAT_PURCHASE = 1004;
	public static final int TOKEN_INVALID = 1005;
}