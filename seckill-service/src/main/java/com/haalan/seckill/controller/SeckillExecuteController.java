package com.haalan.seckill.controller;

import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.domain.dto.SeckillExecuteRequestDTO;
import com.haalan.seckill.domain.vo.SeckillExecuteResultVO;
import com.haalan.seckill.domain.vo.SeckillResultVO;
import com.haalan.seckill.service.ISeckillExecuteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Api(tags = "秒杀执行")
public class SeckillExecuteController {

	private final ISeckillExecuteService seckillExecuteService;

	@PostMapping("/execute")
	@ApiOperation("执行秒杀")
	public R<?> executeSeckill(@RequestBody @Validated SeckillExecuteRequestDTO request) {
		Long userId = UserContext.getUser();

		SeckillExecuteResultVO result = seckillExecuteService.execute(userId, request);

		if (result.getSuccess()) {
			return R.success("秒杀成功，请尽快支付", result.getOrderVO());
		} else {
			return R.success(2001, "排队中，请稍后查询结果", result.getQueueVO());
		}
	}

	/**
	 * 查询秒杀结果
	 * 前端轮询调用此接口查询最终结果
	 */
	@GetMapping("/result/{requestId}")
	@ApiOperation("查询秒杀结果")
	public R<?> querySeckillResult(@PathVariable String requestId) {
		SeckillResultVO result = seckillExecuteService.queryResult(requestId);

		// 根据状态返回不同的响应码和消息
		if ("SUCCESS".equals(result.getStatus())) {
			return R.success("秒杀成功", result);
		} else if ("FAILED".equals(result.getStatus())) {
			// 根据失败原因返回相应的错误码
			String failReason = result.getFailReason();
			if ("STOCK_NOT_ENOUGH".equals(failReason)) {
				return R.success(1003, "库存不足", result);
			} else if ("ACTIVITY_ENDED".equals(failReason)) {
				return R.success(1004, "活动已结束", result);
			} else if ("ACTIVITY_NOT_STARTED".equals(failReason)) {
				return R.success(1005, "活动未开始", result);
			} else if ("ACTIVITY_EXPIRED".equals(failReason)) {
				return R.success(1007, "活动已过期", result);
			} else {
				return R.success(1006, failReason != null ? failReason : "秒杀失败", result);
			}
		} else {
			// PROCESSING
			return R.success(2001, "处理中", result);
		}
	}
}
