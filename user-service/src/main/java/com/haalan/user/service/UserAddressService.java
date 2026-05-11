package com.haalan.user.service;

import com.haalan.user.domain.dto.UserAddressDTO;
import com.haalan.user.domain.vo.AddressVO;
import com.haalan.user.domain.vo.UserAddressVO;

import java.util.List;

public interface UserAddressService {

	AddressVO addAddress(Long userId, UserAddressDTO addressDTO);

	List<UserAddressVO> getUserAddresses(Long userId);

	void updateAddress(Long userId, Long addressId, UserAddressDTO addressDTO);

	void deleteAddress(Long userId, Long addressId);
}
