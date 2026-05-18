package com.haalan.order.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 收货地址信息VO
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Data
@ApiModel("收货地址信息")
public class AddressInfoVO {

	@ApiModelProperty("收货人姓名")
	private String receiverName;

	@ApiModelProperty("收货人电话")
	private String receiverPhone;

	@ApiModelProperty("完整地址")
	private String fullAddress;

	@ApiModelProperty("省份")
	private String province;

	@ApiModelProperty("城市")
	private String city;

	@ApiModelProperty("区县")
	private String district;

	@ApiModelProperty("详细地址")
	private String detailAddress;
}
