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
import com.haalan.user.config.registerProperties;
import com.haalan.user.domain.dto.ChangePasswordDTO;
import com.haalan.user.domain.dto.LoginDTO;
import com.haalan.user.domain.dto.TUserAdminDTO;
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

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class TUserServiceImpl extends ServiceImpl<TUserMapper, TUser> implements TUserService {

	private final TUserMapper tUserMapper;

	private final BCryptPasswordEncoder passwordEncoder;

	private final JwtTool jwtTool;

	private final StringRedisTemplate stringRedisTemplate;

	@Resource
	private registerProperties registerProperties;
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
	public TUserVO register(TUserAdminDTO userDTO) {
		TUserVO tUserVO = new TUserVO();
		TUser user = new TUser();
		BeanUtils.copyProperties(userDTO, user);
		//todo 数据安全 ,这里直接明文传管理员了, 需要用户传给的唯一加密字符串,使用后不能再使用,等待升级;
		if (Objects.equals(userDTO.getAdmin(), registerProperties.getUser())) {
			user.setTokenVersion(0);
		} else {
			user.setTokenVersion(1);
		}

		// 密码加密
		// 设置传入数据库的密码//再做一层 BCrypt（关键） // 前端传来的“加密密码”
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
		userInfoVO.setAvatarUpdateTime(user.getAvatarUpdateTime());


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
		user.setId(userInfoVO.getUserId());
		user.setEmail(userInfoVO.getEmail());
		user.setPhone(userInfoVO.getPhone());
		user.setAvatar(userInfoVO.getAvatar());
		user.setUsername(userInfoVO.getUsername());
		log.info("用户{}修改信息成功", userInfoVO.getAvatar());
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
			Integer newVersion = null;
			//如果是管理员继续设置成0
			newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() == 0 ? -1 : user.getTokenVersion() : 1) + 1;

			user.setTokenVersion(newVersion);
			tUserMapper.updateById(user);

			String redisKey = "user:token:version:" + userId;
			stringRedisTemplate.opsForValue().set(redisKey, newVersion.toString(), 2, TimeUnit.HOURS);

			log.info("用户{}退出登录，Token版本已更新为: {}，Redis已同步", userId, newVersion);
		}
	}

	/**
	 * <p>
	 * 修改密码
	 * </p>
	 *
	 * @param userId            用户ID
	 * @param changePasswordDTO 修改密码请求参数
	 * @author Haaland
	 * @date 2026/5/20
	 */
	@Override
	@Transactional
	public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
		// 1. 验证新密码和确认密码是否一致
		if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmNewPassword())) {
			throw new CommonException("新密码与确认密码不一致", 400);
		}

		// 2. 查询用户信息
		TUser user = tUserMapper.selectById(userId);
		if (user == null) {
			throw new CommonException("用户不存在", 404);
		}

		// 3. 验证原密码是否正确
		if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPassword())) {
			throw new CommonException("原密码错误", 400);
		}

		// 4. 验证新密码不能与原密码相同
		if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPassword())) {
			throw new CommonException("新密码不能与原密码相同", 400);
		}

		// 5. 更新密码并递增token版本使旧token失效
		String encodedNewPassword = passwordEncoder.encode(changePasswordDTO.getNewPassword());
		user.setPassword(encodedNewPassword);

		// 递增token版本，使之前的所有token失效
		Integer newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
		user.setTokenVersion(newVersion);
		user.setUpdateTime(LocalDateTime.now());

		tUserMapper.updateById(user);

		// 6. 更新Redis中的token版本
		String redisKey = "user:token:version:" + userId;
		stringRedisTemplate.opsForValue().set(redisKey, newVersion.toString(), 2, TimeUnit.HOURS);

		log.info("用户{}修改密码成功，Token版本已更新为: {}", userId, newVersion);
	}

	@Transactional
	@Override
	public void updateStatus(TUser user) {

		//同步Redis
		Long userId = user.getId();
		user.setTokenVersion(user.getTokenVersion() == 0 ? 0 : user.getTokenVersion() + 1);
		Integer newVersion = user.getTokenVersion();
		//封禁用户
		tUserMapper.updateById(user);


	}

}
