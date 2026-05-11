package com.haalan.user.domain.po;

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
 * 用户地址表
 * </p>
 *
 * @author lyc
 * @since 2026-04-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_user_address")
@ApiModel(value = "TUserAddress对象", description = "用户地址表")
public class TUserAddress implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "地址ID")
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@ApiModelProperty(value = "用户ID")
	@TableField("user_id")
	private Long userId;

	@ApiModelProperty(value = "收货人姓名")
	@TableField("receiver_name")
	private String receiverName;

	@ApiModelProperty(value = "收货人电话")
	@TableField("receiver_phone")
	private String receiverPhone;

	@ApiModelProperty(value = "省份")
	@TableField("province")
	private String province;

	@ApiModelProperty(value = "城市")
	@TableField("city")
	private String city;

	@ApiModelProperty(value = "区/县")
	@TableField("district")
	private String district;

	@ApiModelProperty(value = "详细地址")
	@TableField("detail_address")
	private String detailAddress;

	@ApiModelProperty(value = "是否默认: 0-否 1-是")
	@TableField("is_default")
	private Integer isDefault;

	@ApiModelProperty(value = "是否删除: 0-未删除 1-已删除")
	@TableLogic
	@TableField("deleted")
	private Integer deleted;

	@ApiModelProperty(value = "创建时间")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@ApiModelProperty(value = "更新时间")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;


}
