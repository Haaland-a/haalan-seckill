package com.haalan.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@ApiModel(value = "活动预热结果")
public class SeckillActivityPreheatVO {

	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	@ApiModelProperty(value = "预热商品数量")
	private Integer productCount;

	@ApiModelProperty(value = "活动总库存")
	private Integer totalStock;

	@ApiModelProperty(value = "预热时间")
	private LocalDateTime preheatTime;

	@ApiModelProperty(value = "是否已预热过")
	private Boolean alreadyPreheated;

	@ApiModelProperty(value = "Redis预热状态")
	private Boolean redisPreheated;

	@ApiModelProperty(value = "本地缓存预热状态")
	private Boolean localCachePreheated;

	@ApiModelProperty(value = "预热数据有效期（秒）")
	private Long ttlSeconds;

	@ApiModelProperty(value = "活动状态：0-未开始 1-进行中 2-已结束")
	private Integer activityStatus;
}