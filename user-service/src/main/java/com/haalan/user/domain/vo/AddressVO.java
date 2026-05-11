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
@ApiModel(value = "地址响应VO", description = "添加地址返回结果")
public class AddressVO {

	@ApiModelProperty(value = "地址ID")
	private Long addressId;
}
