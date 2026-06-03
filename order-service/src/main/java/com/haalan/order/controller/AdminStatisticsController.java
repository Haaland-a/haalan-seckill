package com.haalan.order.controller;

import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.order.domain.vo.ActivityStatsVO;
import com.haalan.order.domain.vo.DailyTrendVO;
import com.haalan.order.domain.vo.OverviewStatsVO;
import com.haalan.order.service.IAdminStatisticsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Api(tags = "管理统计")
public class AdminStatisticsController {

	private final IAdminStatisticsService statisticsService;

	@GetMapping("/overview")
	@ApiOperation("平台总览统计")
	public R<OverviewStatsVO> getOverview() {
		return R.success(statisticsService.getOverview());
	}

	@GetMapping("/seckill/{activityId}")
	@ApiOperation("单活动销售统计")
	public R<ActivityStatsVO> getSeckillActivityStats(@PathVariable Long activityId) {
		return R.success(statisticsService.getSeckillActivityStats(activityId));
	}

	@GetMapping("/seckill/list")
	@ApiOperation("活动销售统计列表")
	public R<PageResult<ActivityStatsVO>> getSeckillActivityStatsList(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize) {
		return R.success(statisticsService.getSeckillActivityStatsList(pageNum, pageSize));
	}

	@GetMapping("/trends")
	@ApiOperation("近N天销售趋势")
	public R<List<DailyTrendVO>> getTrends(
			@RequestParam(defaultValue = "7") Integer days) {
		return R.success(statisticsService.getTrends(days));
	}
}
