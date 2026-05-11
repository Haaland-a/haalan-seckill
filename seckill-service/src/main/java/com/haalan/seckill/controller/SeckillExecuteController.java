package com.haalan.seckill.controller;

import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.domain.dto.SeckillExecuteRequestDTO;
import com.haalan.seckill.domain.vo.SeckillExecuteResultVO;
import com.haalan.seckill.service.ISeckillExecuteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
//    @GetMapping("/result/{requestId}")
//    @ApiOperation("查询秒杀结果")
//    public R<?> querySeckillResult(@PathVariable String requestId) {
//        Long userId = UserContext.getUser();
//
//        SeckillQueryResultVO result = seckillExecuteService.queryResult(userId, requestId);
//
//        if (result == null) {
//            return R.fail(1004, "请求不存在");
//        }
//
//        switch (result.getStatus()) {
//            case "PROCESSING":
//                return R.success(2001, "处理中", result);
//            case "SUCCESS":
//                return R.success(200, "秒杀成功", result);
//            case "FAILED":
//                return R.fail(1003, result.getFailReason(), result);
//            default:
//                return R.fail(1005, "未知状态");
//        }
//    }
}
