package com.haalan.user.controller;

import com.haalan.common.domain.R;
import com.haalan.common.utils.BeanUtils;
import com.haalan.common.utils.EncryptUtils;
import com.haalan.common.utils.UserContext;
import com.haalan.user.annotation.Login;
import com.haalan.user.domain.dto.*;
import com.haalan.user.domain.po.TUser;
import com.haalan.user.domain.vo.*;
import com.haalan.user.service.FileUploadService;
import com.haalan.user.service.OssDirectUploadService;
import com.haalan.user.service.TUserService;
import com.haalan.user.service.UserAddressService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Api(tags = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class TUserController {

	private final TUserService tUserService;
	private final UserAddressService userAddressService;
	private final FileUploadService fileUploadService;
	private final OssDirectUploadService ossDirectUploadService;

	@ApiOperation(value = "用户注册")
	@PostMapping("/register")
	public R<TUserVO> register(@RequestBody TUserDTO userDTO) {
		TUserAdminDTO tUser = new TUserAdminDTO();
		BeanUtils.copyProperties(userDTO, tUser);
		TUserVO tUserVO = tUserService.register(tUser);
		log.info("用户注册成功 {}", tUserVO);
		return R.success("注册成功", tUserVO);
	}

	@ApiOperation(value = "用户登录")
	@PostMapping("/login")
	@Login
	public R<LoginVO> login(@RequestBody LoginDTO loginDTO) {
		LoginVO loginVO = tUserService.login(loginDTO);
		log.info("用户登录成功 {}", loginVO);
		return R.success("登录成功", loginVO);
	}

	@ApiOperation(value = "获取用户信息")
	@GetMapping("/info")
	public R<UserInfoVO> getUserInfo() {
		Long userId = UserContext.getUser();
		UserInfoVO userInfoVO = tUserService.getUserInfo(userId);
		log.info("获取用户信息成功, userId: {}", userId);
		return R.success(userInfoVO);
	}

	@ApiOperation(value = "修改用户信息")
	@PutMapping("/info")
	public R<String> updateUserInfo(@RequestBody UserInfoVO userInfoVO) {
		Long userId = UserContext.getUser();
		userInfoVO.setUserId(userId);
		tUserService.updateUserInfo(userInfoVO);
		log.info("修改用户信息成功, userId: {}", userId);
		return R.success("修改成功");
	}

//	@ApiOperation(value = "上传头像")
//	@PostMapping("/avatar/upload")
//	public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
//		Long userId = UserContext.getUser();
//		String avatarUrl = fileUploadService.uploadAvatar(file);
//		log.info("用户上传头像成功, userId: {}, url: {}", userId, avatarUrl);
//		return R.success("上传成功", avatarUrl);
//	}

	@ApiOperation(value = "获取OSS上传凭证（前端直传）")
	@GetMapping("/oss/credential")
	public R<OssUploadCredentialVO> getOssUploadCredential() {
		Long userId = UserContext.getUser();
		//检验用户是否一天内上传过
		TUser user = tUserService.getById(userId);
		if (user.getAvatarUpdateTime() != null && user.getAvatarUpdateTime().plusDays(1).isAfter(LocalDateTime.now())) {
			return R.error("今天已上传过头像");
		}
		if (user.getTokenAcquireTime() != null && user.getTokenAcquireTime().plusHours(1).isAfter(LocalDateTime.now())) {
			return R.error("请勿频繁点击上传头像,一小时后重试");
		}
		OssUploadCredentialVO credential = ossDirectUploadService.getUploadCredential(userId);
		log.info("用户获取OSS上传凭证, userId: {}", userId);
		return R.success(credential);
	}

	@ApiOperation(value = "添加收货地址")
	@PostMapping("/address")
	public R<AddressVO> addAddress(@RequestBody UserAddressDTO addressDTO) {
		Long userId = UserContext.getUser();
		AddressVO addressVO = userAddressService.addAddress(userId, addressDTO);
		log.info("添加收货地址成功, userId: {}, addressId: {}", userId, addressVO.getAddressId());
		return R.success("添加成功", addressVO);
	}

	@ApiOperation(value = "查询用户收货地址列表")
	@GetMapping("/address")
	public R<List<UserAddressVO>> getUserAddresses() {
		Long userId = UserContext.getUser();
		List<UserAddressVO> addresses = userAddressService.getUserAddresses(userId);
		log.info("查询用户地址列表成功, userId: {}, count: {}", userId, addresses.size());
		return R.success(addresses);
	}

	@ApiOperation(value = "根据ID获取收货地址详情")
	@GetMapping("/address/{addressId}")
	public R<UserAddressVO> getAddressById(@PathVariable String addressId) {
		Long userId = UserContext.getUser();
		// 解密地址ID
		Long decryptedAddressId = EncryptUtils.decryptToLong(addressId);
		UserAddressVO address = userAddressService.getAddressById(userId, decryptedAddressId);
		if (address == null) {
			return R.error("地址不存在或无权限");
		}
		log.info("获取地址详情成功, userId: {}, addressId: {}", userId, decryptedAddressId);
		return R.success(address);
	}

	@ApiOperation(value = "修改收货地址")
	@PutMapping("/address/{addressId}")
	public R<Void> updateAddress(
			@PathVariable String addressId,
			@RequestBody UserAddressDTO addressDTO) {
		Long userId = UserContext.getUser();
		// 解密地址ID
		Long decryptedAddressId = EncryptUtils.decryptToLong(addressId);
		userAddressService.updateAddress(userId, decryptedAddressId, addressDTO);
		return R.success("修改成功", null);
	}

	@ApiOperation(value = "删除收货地址")
	@DeleteMapping("/address/{addressId}")
	public R<Void> deleteAddress(@PathVariable String addressId) {
		Long userId = UserContext.getUser();
		// 解密地址ID
		Long decryptedAddressId = EncryptUtils.decryptToLong(addressId);
		userAddressService.deleteAddress(userId, decryptedAddressId);
		return R.success("删除成功", null);
	}

	@ApiOperation(value = "退出登录")
	@PostMapping("/logout")
	public R<Void> logout() {
		Long userId = UserContext.getUser();
		log.info("退出登录接口被调用, userId from UserContext: {}", userId);

		if (userId == null) {
			return R.error("未登录或Token无效");
		}

		tUserService.logout(userId);
		log.info("用户退出登录, userId: {}", userId);
		return R.success("退出成功", null);
	}

	@ApiOperation(value = "修改密码")
	@PostMapping("/change-password")
	public R<Void> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
		Long userId = UserContext.getUser();
		log.info("修改密码接口被调用, userId: {}", userId);

		if (userId == null) {
			return R.error("未登录或Token无效");
		}

		tUserService.changePassword(userId, changePasswordDTO);
		log.info("用户修改密码成功, userId: {}", userId);
		return R.success("密码修改成功，请重新登录", null);
	}

	/**
	 * 内部接口，用于获取收货地址详情
	 *
	 * @param addressId
	 * @return
	 */
	@GetMapping("address/inner/{addressId}")
	public UserAddressVO getInnerAddressById(@RequestParam Long addressId,
											 @RequestParam Long userId) {

		UserAddressVO address = userAddressService.getAddressById(userId, addressId);
		log.info("获取地址详情成功, userId: {}, addressId: {}", userId, addressId);
		return address;
	}


}
