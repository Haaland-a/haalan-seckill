package com.haalan.user.service.Impl;

import com.haalan.user.domain.po.UserLoginLog;
import com.haalan.user.mapper.UserLoginLogMapper;
import com.haalan.user.service.UserLoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginLogServiceImpl implements UserLoginLogService {

	private final UserLoginLogMapper userLoginLogMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveLoginLog(UserLoginLog loginLog) {
		try {
			// 关键修复：设置创建时间
			if (loginLog.getCreateTime() == null) {
				loginLog.setCreateTime(LocalDateTime.now());
			}

			userLoginLogMapper.insert(loginLog);
			log.debug("登录日志保存成功: userId={}, username={}",
					loginLog.getUserId(), loginLog.getUsername());
		} catch (Exception e) {
			log.error("保存登录日志失败: userId={}, username={}",
					loginLog.getUserId(), loginLog.getUsername(), e);
			throw e;
		}
	}
}