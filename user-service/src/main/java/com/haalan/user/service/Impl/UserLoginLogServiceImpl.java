package com.haalan.user.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.user.domain.po.UserLoginLog;
import com.haalan.user.mapper.UserLoginLogMapper;
import com.haalan.user.service.UserLoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户登录日志Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginLogServiceImpl extends ServiceImpl<UserLoginLogMapper, UserLoginLog> implements UserLoginLogService {

	private final UserLoginLogMapper userLoginLogMapper;

	/**
	 * 保存登录日志
	 * 使用REQUIRES_NEW传播级别，确保日志记录独立于主事务
	 *
	 * @param loginLog 登录日志对象
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveLoginLog(UserLoginLog loginLog) {
		try {
			userLoginLogMapper.insert(loginLog);
			log.info("保存登录日志成功: userId={}, username={}, ip={}, result={}",
					loginLog.getUserId(), loginLog.getUsername(),
					loginLog.getLoginIp(), loginLog.getLoginResult());
		} catch (Exception e) {
			log.error("保存登录日志失败: userId={}, username={}",
					loginLog.getUserId(), loginLog.getUsername(), e);
			// 日志记录失败不影响主业务，只记录错误
		}
	}
}
