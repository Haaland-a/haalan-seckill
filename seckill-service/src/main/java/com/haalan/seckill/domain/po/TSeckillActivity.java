package com.haalan.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀活动表
 * </p>
 *
 * @author lyc
 * @since 2026-04-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_seckill_activity")
@ApiModel(value = "TSeckillActivity对象", description = "秒杀活动表")
public class TSeckillActivity implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "活动ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "活动名称")
	@TableField("activity_name")
	private String activityName;

	@ApiModelProperty(value = "开始时间")
	@TableField("start_time")
	private LocalDateTime startTime;

	@ApiModelProperty(value = "结束时间")
	@TableField("end_time")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "状态: 0-未开始 1-进行中 2-已结束")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "活动描述")
	@TableField("activity_desc")
	private String activityDesc;

	@ApiModelProperty(value = "每人限购数量")
	@TableField("limit_per_user")
	private Integer limitPerUser;

	@ApiModelProperty(value = "活动总库存")
	@TableField("total_stock")
	private Integer totalStock;

	@ApiModelProperty(value = "乐观锁版本号")
	@TableField("version")
	private Integer version;

	@ApiModelProperty(value = "创建时间")
	@TableField("create_time")
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField("update_time")
	private LocalDateTime updateTime;


}
