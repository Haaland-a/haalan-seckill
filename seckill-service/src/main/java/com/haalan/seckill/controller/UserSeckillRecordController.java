package com.haalan.seckill.controller;

import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.domain.vo.UserSeckillRecordVO;
import com.haalan.seckill.service.IUserSeckillRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户秒杀记录 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-13
 */
@RestController
@RequestMapping("/api/seckill/user")
@RequiredArgsConstructor
@Api(tags = "用户秒杀记录")
public class UserSeckillRecordController {

	private final IUserSeckillRecordService userSeckillRecordService;

	@GetMapping("/records")
	@ApiOperation("查询用户秒杀记录")
	public R<PageResult<UserSeckillRecordVO>> getUserRecords(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize) {

		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 查询用户秒杀记录
		PageResult<UserSeckillRecordVO> result = userSeckillRecordService.getUserRecords(userId, pageNum, pageSize);

		return R.success(result);
	}
}
