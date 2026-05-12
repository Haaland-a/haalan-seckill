package com.haalan.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 秒杀日志表
 * </p>
 *
 * @author haaland
 * @since 2026-05-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_seckill_log")
@ApiModel(value="TSeckillLog对象", description="秒杀日志表")
public class TSeckillLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "活动ID")
    @TableField("activity_id")
    private Long activityId;

    @ApiModelProperty(value = "SKU ID")
    @TableField("sku_id")
    private Long skuId;

    @ApiModelProperty(value = "商品ID")
    @TableField("product_id")
    private Long productId;

    @ApiModelProperty(value = "操作类型: REQUEST/LOCK/SUCCESS/FAIL")
    @TableField("action")
    private String action;

    @ApiModelProperty(value = "IP地址")
    @TableField("ip")
    private String ip;

    @ApiModelProperty(value = "用户代理")
    @TableField("user_agent")
    private String userAgent;

    @ApiModelProperty(value = "失败原因")
    @TableField("fail_reason")
    private String failReason;

    @ApiModelProperty(value = "耗时(ms)")
    @TableField("cost_time")
    private Integer costTime;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;


}
