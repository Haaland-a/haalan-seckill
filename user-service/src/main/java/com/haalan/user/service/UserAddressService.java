package com.haalan.user.service;

import com.haalan.user.domain.dto.UserAddressDTO;
import com.haalan.user.domain.vo.AddressVO;
import com.haalan.user.domain.vo.UserAddressVO;

import java.util.List;

public interface UserAddressService {

	AddressVO addAddress(Long userId, UserAddressDTO addressDTO);

	List<UserAddressVO> getUserAddresses(Long userId);

	/**
	 * 根据地址ID获取地址详情
	 *
	 * @param userId    用户ID
	 * @param addressId 地址ID
	 * @return 地址详情
	 */
	UserAddressVO getAddressById(Long userId, Long addressId);

	void updateAddress(Long userId, Long addressId, UserAddressDTO addressDTO);

	void deleteAddress(Long userId, Long addressId);
}
