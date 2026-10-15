package com.haalan.order.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ApiModel("创建订单请求")
public class CreateOrderRequestDTO {

	/**
	 * 收货地址 ID。
	 * <p>
	 * 这里必须是 String 而非 Long：user-service 的地址接口对外一律返回
	 * {@link com.haalan.common.utils.EncryptUtils} 加密后的字符串（见 TUserController 的
	 * /address 系列接口，内部用 EncryptUtils.decryptToLong 还原），前端会把拿到的
	 * addressId 原样回传。若声明为 Long，Jackson 反序列化加密串会直接抛
	 * InvalidFormatException（Cannot deserialize value of type `java.lang.Long`
	 * from String "etZBPk..."），表现为「提交订单 → 500 服务器内部错误」。
	 * 故此处收 String，由 service 层调用 EncryptUtils.decryptToLong 解密。
	 */
	@NotNull(message = "收货地址不能为空")
	@ApiModelProperty(value = "收货地址ID（加密字符串，也兼容历史的纯数字串）", required = true)
	private String addressId;

	@ApiModelProperty(value = "备注")
	private String remark;

	@NotEmpty(message = "商品列表不能为空")
	@Valid
	@ApiModelProperty(value = "商品列表", required = true)
	private List<OrderItemDTO> items;
}
