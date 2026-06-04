package com.haalan.seckill.controller;

import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import com.haalan.common.domain.R;
import com.haalan.seckill.domain.vo.SeckillActivityCacheVO;
import com.haalan.seckill.domain.vo.SeckillActivityDetailVO;
import com.haalan.seckill.domain.vo.SeckillProductInfoVO;
import com.haalan.seckill.service.ITSeckillActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Api(tags = "秒杀活动查询（用户端）")
public class SeckillActivityQueryController {

	private final ITSeckillActivityService seckillActivityService;

	@GetMapping("/activities")
	@ApiOperation("查询秒杀活动列表（从Redis缓存）")
	public R<List<SeckillActivityCacheVO>> getActivityList(
			@ApiParam(value = "活动状态: 0-未开始 1-进行中 2-已结束", example = "1")
			@RequestParam(required = false) Integer status) {
		List<SeckillActivityCacheVO> list = seckillActivityService.getActivityList(status);
		return R.success(list);
	}

	@GetMapping("/activities/all")
	@ApiOperation("查询所有秒杀活动（内部调用，从DB）")
	public List<SeckillActivityBriefVO> getAllActivities() {
		return seckillActivityService.getAllActivities();
	}

	@GetMapping("/activity/{activityId}/products")
	@ApiOperation("查询秒杀活动详情（含商品列表）")
	public R<SeckillActivityDetailVO> getActivityDetail(
			@ApiParam(value = "活动ID", required = true)
			@PathVariable Long activityId) {
		SeckillActivityDetailVO detail = seckillActivityService.getActivityDetail(activityId);
		return R.success(detail);
	}

	@GetMapping("/product/{seckillProductId}")
	@ApiOperation("查询秒杀商品详情")
	public R<SeckillProductInfoVO> getProductDetail(
			@ApiParam(value = "秒杀商品ID", required = true)
			@PathVariable Long seckillProductId,
			@RequestParam Long activityId) {
		SeckillProductInfoVO detail = seckillActivityService.getProductDetail(seckillProductId, activityId);
		return R.success(detail);
	}
}
