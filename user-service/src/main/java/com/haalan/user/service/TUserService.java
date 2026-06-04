/**
 * @description TUserService
 * @author
 * @date 2026/4/13
 */
package com.haalan.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.user.domain.dto.ChangePasswordDTO;
import com.haalan.user.domain.dto.LoginDTO;
import com.haalan.user.domain.dto.TUserAdminDTO;
import com.haalan.user.domain.po.TUser;
import com.haalan.user.domain.vo.LoginVO;
import com.haalan.user.domain.vo.TUserVO;
import com.haalan.user.domain.vo.UserInfoVO;

public interface TUserService extends IService<TUser> {
	TUserVO register(TUserAdminDTO userDTO);

	LoginVO login(LoginDTO loginDTO);

	UserInfoVO getUserInfo(Long userId);

	void updateUserInfo(UserInfoVO userInfoVO);

	void logout(Long userId);

	/**
	 * 修改密码
	 *
	 * @param userId            用户ID
	 * @param changePasswordDTO 修改密码请求参数
	 */
	void changePassword(Long userId, ChangePasswordDTO changePasswordDTO);

	void updateStatus(TUser user);
}
