package com.haalan.user.service;

import com.haalan.user.domain.po.UserLoginLog;

/**
 * 用户登录日志Service接口
 */
public interface UserLoginLogService {

	/**
	 * 保存登录日志
	 *
	 * @param loginLog 登录日志对象
	 */
	void saveLoginLog(UserLoginLog loginLog);
}
