package com.haalan.api.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户地址 VO
 */
@Data
@ApiModel("用户地址信息")
public class UserAddressVO {

	@ApiModelProperty("地址ID（加密后）")
	private String addressId;

	@ApiModelProperty("收货人姓名")
	private String receiverName;

	@ApiModelProperty("收货人电话")
	private String receiverPhone;

	@ApiModelProperty("省份")
	private String province;

	@ApiModelProperty("城市")
	private String city;

	@ApiModelProperty("区县")
	private String district;

	@ApiModelProperty("详细地址")
	private String detailAddress;

	@ApiModelProperty("完整地址")
	private String fullAddress;

	@ApiModelProperty("是否默认地址")
	private Boolean isDefault;
}
