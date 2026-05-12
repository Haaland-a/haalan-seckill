package com.haalan.seckill.domain.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户秒杀行为日志（与数据库表 t_seckill_log 对应）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillUserLogMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 活动ID
	 */
	private Long activityId;

	/**
	 * SKU ID
	 */
	private Long skuId;

	/**
	 * 商品ID
	 */
	private Long productId;

	/**
	 * 操作类型: REQUEST/LOCK/SUCCESS/FAIL
	 */
	private String action;

	/**
	 * IP地址
	 */
	private String ip;

	/**
	 * 用户代理
	 */
	private String userAgent;

	/**
	 * 失败原因
	 */
	private String failReason;

	/**
	 * 耗时(ms)
	 */
	private Integer costTime;

	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;
}
