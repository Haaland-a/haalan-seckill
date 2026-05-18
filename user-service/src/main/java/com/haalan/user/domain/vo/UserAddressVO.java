package com.haalan.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "地址信息VO", description = "收货地址详细信息")
public class UserAddressVO {

	@ApiModelProperty(value = "地址ID")
	private String addressId;

	@ApiModelProperty(value = "收货人姓名")
	private String receiverName;

	@ApiModelProperty(value = "收货人电话(脱敏)")
	private String receiverPhone;

	@ApiModelProperty(value = "省份")
	private String province;

	@ApiModelProperty(value = "城市")
	private String city;

	@ApiModelProperty(value = "区县")
	private String district;

	@ApiModelProperty(value = "详细地址")
	private String detailAddress;

	@ApiModelProperty(value = "完整地址")
	private String fullAddress;

	@ApiModelProperty(value = "是否默认地址")
	private Boolean isDefault;
}
