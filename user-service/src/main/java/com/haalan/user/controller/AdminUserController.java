package com.haalan.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.user.domain.dto.AdminUserQueryDTO;
import com.haalan.user.domain.dto.TUserAdminDTO;
import com.haalan.user.domain.dto.UserStatusUpdateDTO;
import com.haalan.user.domain.po.TUser;
import com.haalan.user.domain.po.UserLoginLog;
import com.haalan.user.domain.vo.AdminUserListVO;
import com.haalan.user.domain.vo.LoginLogVO;
import com.haalan.user.domain.vo.TUserVO;
import com.haalan.user.mapper.UserLoginLogMapper;
import com.haalan.user.service.TUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Api(tags = "管理端用户管理")
public class AdminUserController {

	private final TUserService userService;
	private final UserLoginLogMapper loginLogMapper;
	private final StringRedisTemplate stringRedisTemplate;

	@PostMapping("/register")
	@ApiOperation("管理端注册用户")
	public R<TUserVO> register(@RequestBody TUserAdminDTO userDTO) {
		TUserVO result = userService.register(userDTO);
		return R.success("注册成功", result);
	}

	@GetMapping("/count")
	@ApiOperation("获取用户总数（内部调用）")
	public Long getUserCount() {
		return userService.count();
	}

	@GetMapping
	@ApiOperation("用户列表")
	public R<PageResult<AdminUserListVO>> listUsers(AdminUserQueryDTO query) {
		LambdaQueryWrapper<TUser> wrapper = new LambdaQueryWrapper<>();
		if (StringUtils.hasText(query.getUsername())) {
			wrapper.like(TUser::getUsername, query.getUsername());
		}
		if (StringUtils.hasText(query.getPhone())) {
			wrapper.like(TUser::getPhone, query.getPhone());
		}
		if (query.getStatus() != null) {
			wrapper.eq(TUser::getStatus, query.getStatus());
		}
		if (query.getMemberLevel() != null) {
			wrapper.eq(TUser::getMemberLevel, query.getMemberLevel());
		}
		wrapper.orderByDesc(TUser::getCreateTime);

		Page<TUser> page = new Page<>(query.getPageNum(), query.getPageSize());
		Page<TUser> result = userService.page(page, wrapper);

		List<AdminUserListVO> voList = result.getRecords().stream()
				.map(this::convertToListVO)
				.collect(Collectors.toList());

		PageResult<AdminUserListVO> pageResult = new PageResult<>();
		pageResult.setTotal(result.getTotal());
		pageResult.setPageNum((int) result.getCurrent());
		pageResult.setPageSize((int) result.getSize());
		pageResult.setList(voList);
		return R.success(pageResult);
	}

	@GetMapping("/{userId}")
	@ApiOperation("用户详情")
	public R<TUser> getUserDetail(@PathVariable Long userId) {
		TUser user = userService.getById(userId);
		if (user == null) {
			throw new BizIllegalException("用户不存在");
		}
		user.setPassword(null);
		return R.success(user);
	}

	@PutMapping("/{userId}/status")
	@ApiOperation("启用/禁用用户")
	public R<Void> updateUserStatus(@PathVariable Long userId,
									@RequestBody @Validated UserStatusUpdateDTO dto) {
		TUser user = userService.getById(userId);
		if (user == null) {
			throw new BizIllegalException("用户不存在");
		}
		user.setStatus(dto.getStatus());
		// 禁用后让已签发的 Token 立即失效：token_version + 1 会让网关侧
		// 的版本比对判定旧 Token 过期（网关 Redis 里缓存的是递增后的新版本）。
		// 原来这里写的是 user.setTokenVersion(user.getTokenVersion()) 自赋值，
		// 是个空操作 —— 禁用账号后旧 Token 依然能用到自然过期。
		Integer currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
		user.setTokenVersion(currentVersion + 1);
		// 同步到 redis,检查是在redis中检查的
		stringRedisTemplate.opsForValue().set(
				"user:token:version:" + userId,
				String.valueOf(currentVersion + 1),
				2, java.util.concurrent.TimeUnit.HOURS);

		userService.updateStatus(user);
		return R.success("状态更新成功");
	}

	@GetMapping("/login-logs")
	@ApiOperation("登录日志列表")
	public R<PageResult<LoginLogVO>> getLoginLogs(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "20") Integer pageSize,
			@RequestParam(required = false) Long userId) {

		LambdaQueryWrapper<UserLoginLog> wrapper = new LambdaQueryWrapper<>();
		if (userId != null) {
			wrapper.eq(UserLoginLog::getUserId, userId);
		}
		wrapper.orderByDesc(UserLoginLog::getLoginTime);

		Page<UserLoginLog> page = new Page<>(pageNum, pageSize);
		Page<UserLoginLog> result = loginLogMapper.selectPage(page, wrapper);

		List<LoginLogVO> voList = result.getRecords().stream()
				.map(this::convertToLogVO)
				.collect(Collectors.toList());

		PageResult<LoginLogVO> pageResult = new PageResult<>();
		pageResult.setTotal(result.getTotal());
		pageResult.setPageNum((int) result.getCurrent());
		pageResult.setPageSize((int) result.getSize());
		pageResult.setList(voList);
		return R.success(pageResult);
	}

	private AdminUserListVO convertToListVO(TUser user) {
		AdminUserListVO vo = new AdminUserListVO();
		vo.setUserId(user.getId());
		vo.setUsername(user.getUsername());
		vo.setPhone(user.getPhone());
		vo.setEmail(user.getEmail());
		vo.setStatus(user.getStatus());
		vo.setStatusName(user.getStatus() == 1 ? "正常" : "禁用");
		vo.setMemberLevel(user.getMemberLevel());
		vo.setMemberLevelName(getMemberLevelName(user.getMemberLevel()));
		vo.setCreateTime(user.getCreateTime());
		return vo;
	}

	private LoginLogVO convertToLogVO(UserLoginLog log) {
		LoginLogVO vo = new LoginLogVO();
		vo.setId(log.getId());
		vo.setUserId(log.getUserId());
		vo.setUsername(log.getUsername());
		vo.setLoginIp(log.getLoginIp());
		vo.setLoginTime(log.getLoginTime());
		vo.setLoginResult(log.getLoginResult());
		vo.setLoginResultName(log.getLoginResult() == 1 ? "成功" : "失败");
		vo.setFailReason(log.getFailReason());
		vo.setBrowser(log.getBrowser());
		vo.setOs(log.getOs());
		return vo;
	}

	private String getMemberLevelName(Integer level) {
		if (level == null) return "普通";
		return switch (level) {
			case 0 -> "普通";
			case 1 -> "白银";
			case 2 -> "黄金";
			case 3 -> "钻石";
			default -> "普通";
		};
	}
}
