package com.haalan.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "收货地址DTO", description = "添加/修改收货地址请求参数")
public class UserAddressDTO {

	@ApiModelProperty(value = "收货人姓名")
	private String receiverName;

	@ApiModelProperty(value = "收货人电话")
	private String receiverPhone;

	@ApiModelProperty(value = "省份")
	private String province;

	@ApiModelProperty(value = "城市")
	private String city;

	@ApiModelProperty(value = "区县")
	private String district;

	@ApiModelProperty(value = "详细地址")
	private String detailAddress;

	@ApiModelProperty(value = "是否默认地址")
	private Boolean isDefault;
}
