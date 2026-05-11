/**
 * @description TUserServiceImpl
 * @author Haaland
 * @date 2026/4/13
 */
package com.haalan.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.exception.CommonException;
import com.haalan.common.utils.BeanUtils;
import com.haalan.user.domain.dto.LoginDTO;
import com.haalan.user.domain.dto.TUserDTO;
import com.haalan.user.domain.po.TUser;
import com.haalan.user.domain.vo.LoginVO;
import com.haalan.user.domain.vo.TUserVO;
import com.haalan.user.domain.vo.UserInfoVO;
import com.haalan.user.enums.MemberLevel;
import com.haalan.user.enums.UserStatus;
import com.haalan.user.mapper.TUserMapper;
import com.haalan.user.service.TUserService;
import com.haalan.user.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class TUserServiceImpl extends ServiceImpl<TUserMapper, TUser> implements TUserService {

	private final TUserMapper tUserMapper;

	private final BCryptPasswordEncoder passwordEncoder;

	private final JwtTool jwtTool;

	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * <p>
	 * 注册
	 * </p>
	 *
	 * @param userDTO
	 * @return TUserVO
	 * @author Haaland
	 * @date 2026/4/13
	 */
	@Override
	public TUserVO register(TUserDTO userDTO) {
		TUserVO tUserVO = new TUserVO();
		TUser user = new TUser();
		BeanUtils.copyProperties(userDTO, user);

		// 密码加密
		// 设置传入数据库的密码//再做一层 BCrypt（关键🔥） // 前端传来的“加密密码”
		user.setPassword(passwordEncoder.encode(user.getPassword()));

		user.setCreateTime(LocalDateTime.now());
		user.setUpdateTime(LocalDateTime.now());
		user.setStatus(UserStatus.NORMAL.getCode());
		user.setMemberLevel(MemberLevel.NORMAL.getCode());
		user.setAvatar("https://lyc1019.oss-cn-beijing.aliyuncs.com/2026/03/67b51838-5dde-4f50-afd4-1af69a7e591f.jpg"); //默认头像
		tUserMapper.register(user);
		tUserVO.setUserId(user.getId());
		tUserVO.setUsername(user.getUsername());
		return tUserVO;
	}

	/**
	 * <p>
	 * 登陆
	 * </p>
	 *
	 * @param loginDTO
	 * @return LoginVO
	 * @author Haaland
	 * @date 2026/4/13
	 */
	@Override
	public LoginVO login(LoginDTO loginDTO) {
		TUser user = tUserMapper.selectOne(
				new QueryWrapper<TUser>()
						.eq("username", loginDTO.getUsername()));
		log.info("用户{}查询成功", user);
		if (user == null) {
			throw new CommonException("用户名或密码错误", 401);
		}

		if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
			throw new CommonException("用户名或密码错误", 401);
		}

		if (user.getStatus() != null && user.getStatus().equals(UserStatus.DISABLE.getCode())) {
			throw new CommonException("账号已被禁用", 403);
		}

		Integer version = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
		String token = jwtTool.createToken(user.getId(), version, /*Duration.ofHours(2)*/ Duration.ofDays(50)/*TODO 这是测试时用的*/);

		LoginVO loginVO = new LoginVO();
		log.info("用户{}登录成功，生成Token(version:{}):{}", user.getUsername(), version, token);
		loginVO.setUserId(user.getId());
		loginVO.setUsername(user.getUsername());
		loginVO.setToken(token);
		loginVO.setTokenExpire(System.currentTimeMillis() + Duration.ofHours(2).toMillis());
		loginVO.setMemberLevel(user.getMemberLevel());

		return loginVO;
	}

	/**
	 * <p>
	 * 获取用户信息
	 * </p>
	 *
	 * @param
	 * @return
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	public UserInfoVO getUserInfo(Long userId) {
		TUser user = tUserMapper.selectById(userId);

		if (user == null) {
			throw new CommonException("用户不存在", 404);
		}

		UserInfoVO userInfoVO = new UserInfoVO();
		userInfoVO.setUserId(user.getId());
		userInfoVO.setUsername(user.getUsername());
		userInfoVO.setPhone(user.getPhone());
		userInfoVO.setEmail(user.getEmail());
		userInfoVO.setAvatar(user.getAvatar());
		userInfoVO.setMemberLevel(user.getMemberLevel());
		userInfoVO.setLastLoginTime(user.getUpdateTime());

		log.info("用户{}查询成功", user);
		return userInfoVO;
	}

	/**
	 * <p>
	 * 修改用户信息
	 * </p>
	 *
	 * @param userInfoVO
	 * @return void
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	public void updateUserInfo(UserInfoVO userInfoVO) {
		TUser user = new TUser();
		BeanUtils.copyProperties(userInfoVO, user);
		tUserMapper.updateById(user);
	}

	/**
	 * <p>
	 * 退出登录 - 递增tokenVersion使旧Token失效
	 * </p>
	 *
	 * @param userId 用户ID
	 * @author Haaland
	 * @date 2026/4/14
	 */
	@Override
	@Transactional
	public void logout(Long userId) {
		TUser user = tUserMapper.selectById(userId);
		if (user != null) {
			Integer newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
			user.setTokenVersion(newVersion);
			tUserMapper.updateById(user);

			String redisKey = "user:token:version:" + userId;
			stringRedisTemplate.opsForValue().set(redisKey, newVersion.toString(), 2, TimeUnit.HOURS);

			log.info("用户{}退出登录，Token版本已更新为: {}，Redis已同步", userId, newVersion);
		}
	}

}
