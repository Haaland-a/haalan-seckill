package com.haalan.search.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 商品分类表
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_category")
@ApiModel(value = "TCategory对象", description = "商品分类表")
public class TCategory implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "分类ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "父分类ID")
	@TableField("parent_id")
	private Long parentId;

	@ApiModelProperty(value = "分类名称")
	@TableField("name")
	private String name;

	@ApiModelProperty(value = "分类层级")
	@TableField("level")
	private Integer level;

	@ApiModelProperty(value = "排序")
	@TableField("sort")
	private Integer sort;

	@ApiModelProperty(value = "状态: 0-禁用 1-正常")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;


}
