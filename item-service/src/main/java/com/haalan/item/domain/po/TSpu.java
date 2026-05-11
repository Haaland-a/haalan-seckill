package com.haalan.item.domain.po;

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
 * 商品SPU表
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_spu")
@ApiModel(value = "TSpu对象", description = "商品SPU表")
public class TSpu implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SPU ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "SPU编码")
	@TableField("spu_code")
	private String spuCode;

	@ApiModelProperty(value = "商品名称")
	@TableField("name")
	private String name;

	@ApiModelProperty(value = "分类ID")
	@TableField("category_id")
	private Long categoryId;

	@ApiModelProperty(value = "品牌ID")
	@TableField("brand_id")
	private Long brandId;

	@ApiModelProperty(value = "商品描述")
	@TableField("description")
	private String description;

	@ApiModelProperty(value = "主图URL")
	@TableField("main_image")
	private String mainImage;

	@ApiModelProperty(value = "商品图片列表")
	@TableField("images")
	private String images;

	@ApiModelProperty(value = "状态: 0-下架 1-上架")
	@TableField("status")
	private Integer status;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;


}
