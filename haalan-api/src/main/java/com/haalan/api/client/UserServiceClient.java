package com.haalan.api.client;

import com.haalan.api.domain.vo.UserAddressVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(value = "user-service")
public interface UserServiceClient {

	/**
	 * 根据地址ID获取用户地址详情
	 *
	 * @param addressId 地址ID
	 * @return 地址详情
	 */
	@GetMapping("/api/user/address/inner/{addressId}")
	UserAddressVO getUserAddressById(@RequestParam("addressId") Long addressId,
									 @RequestParam("userId") Long userId);

	/**
	 * 获取用户所有地址列表
	 *
	 * @return 地址列表
	 */
	@GetMapping("/api/user/address")
	List<UserAddressVO> getUserAddresses();
}
