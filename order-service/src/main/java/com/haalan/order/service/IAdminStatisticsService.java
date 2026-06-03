package com.haalan.order.service;

import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.vo.ActivityStatsVO;
import com.haalan.order.domain.vo.DailyTrendVO;
import com.haalan.order.domain.vo.OverviewStatsVO;

import java.util.List;

public interface IAdminStatisticsService {

	OverviewStatsVO getOverview();

	ActivityStatsVO getSeckillActivityStats(Long activityId);

	PageResult<ActivityStatsVO> getSeckillActivityStatsList(Integer pageNum, Integer pageSize);

	List<DailyTrendVO> getTrends(Integer days);
}
