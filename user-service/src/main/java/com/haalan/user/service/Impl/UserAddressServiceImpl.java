package com.haalan.user.service.Impl;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.haalan.user.domain.dto.UserAddressDTO;
import com.haalan.user.domain.po.TUserAddress;
import com.haalan.user.domain.vo.AddressVO;
import com.haalan.user.domain.vo.UserAddressVO;
import com.haalan.user.mapper.TUserAddressMapper;
import com.haalan.user.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressServiceImpl implements UserAddressService {

	private final TUserAddressMapper addressMapper;

	/**
	 * <p>
	 * 添加收货地址
	 * </p>
	 *
	 * @param userId
	 * @param addressDTO
	 * @return
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	@Transactional
	public AddressVO addAddress(Long userId, UserAddressDTO addressDTO) {
		TUserAddress address = new TUserAddress();
		address.setUserId(userId);
		address.setReceiverName(addressDTO.getReceiverName());
		address.setReceiverPhone(addressDTO.getReceiverPhone());
		address.setProvince(addressDTO.getProvince());
		address.setCity(addressDTO.getCity());
		address.setDistrict(addressDTO.getDistrict());
		address.setDetailAddress(addressDTO.getDetailAddress());
		// 是否默认
		address.setIsDefault(addressDTO.getIsDefault() != null && addressDTO.getIsDefault() ? 1 : 0);
		// 如果是默认,查找默认地址,取消默认
		if (address.getIsDefault() == 1) {
			cancelDefaultAddress(userId);
		}

		addressMapper.insert(address);
		log.info("添加收货地址成功, userId: {}, addressId: {}", userId, address.getId());

		return AddressVO.builder().addressId(address.getId()).build();
	}

	/**
	 * <p>
	 * 取消默认地址
	 * </p>
	 *
	 * @param userId
	 * @return
	 * @author Haaland
	 * @date 2026/4/14
	 */
	private void cancelDefaultAddress(Long userId) {
		TUserAddress defaultAddress = new TUserAddress();
		defaultAddress.setIsDefault(0);
		addressMapper.update(defaultAddress,
				Wrappers.<TUserAddress>lambdaQuery()
						.eq(TUserAddress::getUserId, userId)
						.eq(TUserAddress::getIsDefault, 1));
	}

	/**
	 * <p>
	 * 获取用户地址列表
	 * </p>
	 *
	 * @param userId
	 * @return
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	public List<UserAddressVO> getUserAddresses(Long userId) {
		List<TUserAddress> addresses = addressMapper.selectList(
				Wrappers.<TUserAddress>lambdaQuery()
						.eq(TUserAddress::getUserId, userId)
						.orderByDesc(TUserAddress::getIsDefault)
						.orderByDesc(TUserAddress::getCreateTime)
		);
		//需要有一个具体地址:fullAddress, 需要一个脱敏的电话号码:receiverPhone
		//通过字符串拼接获取fullAddress, 通过DesensitizedUtil.mobilePhone获取receiverPhone
		return addresses.stream().map(this::convertToVO).collect(Collectors.toList());
	}

	private UserAddressVO convertToVO(TUserAddress address) {
		String fullAddress = StrUtil.format("{}{}{}{}",
				address.getProvince(),
				address.getCity(),
				address.getDistrict(),
				address.getDetailAddress());
		// 获取手机号码的脱敏结果
		//它主要用于这些场景：
		//DesensitizedUtil
		//📱 手机号脱敏
		//🪪 身份证号脱敏
		//👤 姓名脱敏
		//📧 邮箱脱敏
		//🏠 地址脱敏
		String phoneMasked = DesensitizedUtil.mobilePhone(address.getReceiverPhone());

		return UserAddressVO.builder()
				.addressId(address.getId())
				.receiverName(address.getReceiverName())
				.receiverPhone(phoneMasked)
				.province(address.getProvince())
				.city(address.getCity())
				.district(address.getDistrict())
				.detailAddress(address.getDetailAddress())
				.fullAddress(fullAddress)
				.isDefault(address.getIsDefault() == 1)
				.build();   //最终结果
	}

	/**
	 * <p>
	 * 修改收货地址
	 * </p>
	 *
	 * @param userId
	 * @param addressId
	 * @param addressDTO
	 * @return
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	@Transactional
	public void updateAddress(Long userId, Long addressId, UserAddressDTO addressDTO) {
		TUserAddress existAddress = addressMapper.selectOne(
				Wrappers.<TUserAddress>lambdaQuery()
						.eq(TUserAddress::getId, addressId)
						.eq(TUserAddress::getUserId, userId)
		);

		if (existAddress == null) {
			throw new RuntimeException("地址不存在或无权限");
		}

		if (addressDTO.getReceiverName() != null) {
			existAddress.setReceiverName(addressDTO.getReceiverName());
		}
		if (addressDTO.getReceiverPhone() != null) {
			existAddress.setReceiverPhone(addressDTO.getReceiverPhone());
		}
		if (addressDTO.getProvince() != null) {
			existAddress.setProvince(addressDTO.getProvince());
		}
		if (addressDTO.getCity() != null) {
			existAddress.setCity(addressDTO.getCity());
		}
		if (addressDTO.getDistrict() != null) {
			existAddress.setDistrict(addressDTO.getDistrict());
		}
		if (addressDTO.getDetailAddress() != null) {
			existAddress.setDetailAddress(addressDTO.getDetailAddress());
		}
		if (addressDTO.getIsDefault() != null && addressDTO.getIsDefault()) {
			cancelDefaultAddress(userId);   //先取消是默认地址的 地址
			existAddress.setIsDefault(1);   //设置为默认地址
		}

		addressMapper.updateById(existAddress);
		log.info("修改收货地址成功, userId: {}, addressId: {}", userId, addressId);
	}

	@Override
	@Transactional
	public void deleteAddress(Long userId, Long addressId) {
		TUserAddress existAddress = addressMapper.selectOne(
				Wrappers.<TUserAddress>lambdaQuery()
						.eq(TUserAddress::getId, addressId)
						.eq(TUserAddress::getUserId, userId)
		);
		//MyBatis-Plus自动将deleteById转为UPDATE deleted=1
		//查询时自动过滤deleted=1的数据
		if (existAddress == null) {
			throw new RuntimeException("地址不存在或无权限");
		}

		// 使用@TableLogic自动实现逻辑删除  保留历史数据，便于审计
		addressMapper.deleteById(addressId);
		log.info("删除收货地址成功(逻辑删除), userId: {}, addressId: {}", userId, addressId);
	}

}
