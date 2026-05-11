package com.haalan.seckill.config;


import javax.annotation.Resource;

public class SeckillConstants {

	@Resource
	private STokenProperties sTokenProperties;
	// ==================== Token 相关常量 ====================
	public static final String SECKILL_TOKEN_PREFIX = "seckill:token:";

	public static final String TOKEN_PREFIX = "st_";

	// ==================== 活动相关常量 ====================
	public static final String SECKILL_ACTIVITY_LIST = "seckill:activity:list:";
	public static final String SECKILL_ACTIVITY_STATUS_INDEX = "seckill:activity:status:index:";
	public static final String SECKILL_ACTIVITY_PRODUCTS = "seckill:activity:products:";
	public static final String SECKILL_ACTIVITY_CACHE_PREFIX = "seckill:activity:cache:status:";

	// ==================== 商品相关常量 ====================
	public static final String SECKILL_PRODUCT_PREFIX = "seckill:product:";
	public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

	// ==================== 限购相关常量 ====================
	public static final String SECKILL_USER_LIMIT_PREFIX = "seckill:limit:";
	public static final String SECKILL_ACTIVITY_USER_LIMIT_PREFIX = "seckill:limit:activity:";
	public static final String SECKILL_USER_BUY_PREFIX = "seckill:buy:";


	// ==================== 预热相关常量 ====================
//    public static final String SECKILL_ACTIVITY_STATUS = "seckill:activity:status:";
//    public static final String SECKILL_ACTIVITY_START = "seckill:activity:start:";
//    public static final String SECKILL_ACTIVITY_END = "seckill:activity:end:";
	public static final String SECKILL_PREHEAT_FLAG = "seckill:preheat:flag:";
	public static final String SECKILL_READY = "seckill:ready:";

	// ==================== 幂等相关常量 ====================
	public static final String SECKILL_IDEMPOTENT_PREFIX = "seckill:idempotent:";
	public static final int IDEMPOTENT_EXPIRE_SECONDS = 24 * 60 * 60;

	// ==================== 订单超时相关常量 ====================
	public static final String SECKILL_ORDER_TIMEOUT_PREFIX = "seckill:order:timeout:";

	public static final int ORDER_PAY_TIMEOUT_MINUTES = 15;

	// ===================== 订单相关常量 ====================

	public static final String SECKILL_ORDER_PREFIX = "seckill:order:";


}
