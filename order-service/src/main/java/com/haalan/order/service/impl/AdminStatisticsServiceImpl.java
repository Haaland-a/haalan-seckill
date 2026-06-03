package com.haalan.order.service.impl;

import com.haalan.api.client.SeckillServiceClient;
import com.haalan.api.client.UserServiceClient;
import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import com.haalan.common.domain.PageResult;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.domain.ActivityStatsRaw;
import com.haalan.order.domain.po.TRefund;
import com.haalan.order.domain.vo.ActivityStatsVO;
import com.haalan.order.domain.vo.DailyTrendVO;
import com.haalan.order.domain.vo.OverviewStatsVO;
import com.haalan.order.mapper.TOrderMapper;
import com.haalan.order.service.IAdminStatisticsService;
import com.haalan.order.service.ITRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsServiceImpl implements IAdminStatisticsService {

	private final TOrderMapper orderMapper;
	private final ITRefundService refundService;
	private final UserServiceClient userServiceClient;
	private final SeckillServiceClient seckillServiceClient;

	@Override
	public OverviewStatsVO getOverview() {
		OverviewStatsVO vo = new OverviewStatsVO();

		try {
			vo.setTotalUsers(userServiceClient.getUserCount());
		} catch (Exception e) {
			log.warn("获取用户数失败", e);
			vo.setTotalUsers(0L);
		}

		vo.setTotalOrders(orderMapper.selectCount(null));

		BigDecimal revenue = orderMapper.sumPaidAmount();
		vo.setTotalRevenue(revenue != null ? revenue : BigDecimal.ZERO);

		vo.setPendingRefunds(refundService.lambdaQuery()
				.eq(TRefund::getStatus, 0)
				.count());

		vo.setTodayOrders(orderMapper.countToday());

		BigDecimal todayRevenue = orderMapper.sumTodayAmount();
		vo.setTodayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO);

		return vo;
	}

	@Override
	public ActivityStatsVO getSeckillActivityStats(Long activityId) {
		List<SeckillActivityBriefVO> activities = seckillServiceClient.getAllActivities();
		SeckillActivityBriefVO activity = activities.stream()
				.filter(a -> a.getActivityId().equals(activityId))
				.findFirst()
				.orElseThrow(() -> new BizIllegalException("活动不存在"));

		ActivityStatsRaw raw = orderMapper.getSeckillActivityStatsRawById(activityId);

		return buildActivityStatsVO(activity, raw);
	}

	@Override
	public PageResult<ActivityStatsVO> getSeckillActivityStatsList(Integer pageNum, Integer pageSize) {
		List<SeckillActivityBriefVO> activities = seckillServiceClient.getAllActivities();
		List<ActivityStatsRaw> rawList = orderMapper.getSeckillActivityStatsRaw();

		Map<Long, ActivityStatsRaw> rawMap = rawList.stream()
				.collect(Collectors.toMap(ActivityStatsRaw::getActivityId, r -> r, (a, b) -> a));

		List<ActivityStatsVO> voList = new ArrayList<>();
		for (SeckillActivityBriefVO activity : activities) {
			ActivityStatsRaw raw = rawMap.get(activity.getActivityId());
			voList.add(buildActivityStatsVO(activity, raw));
		}

		voList.sort((a, b) -> {
			if (a.getStartTime() == null && b.getStartTime() == null) return 0;
			if (a.getStartTime() == null) return 1;
			if (b.getStartTime() == null) return -1;
			return b.getStartTime().compareTo(a.getStartTime());
		});

		int start = (pageNum - 1) * pageSize;
		int end = Math.min(start + pageSize, voList.size());
		List<ActivityStatsVO> pagedList = start < voList.size()
				? voList.subList(start, end)
				: new ArrayList<>();

		PageResult<ActivityStatsVO> result = new PageResult<>();
		result.setTotal((long) voList.size());
		result.setPageNum(pageNum);
		result.setPageSize(pageSize);
		result.setList(pagedList);
		return result;
	}

	@Override
	public List<DailyTrendVO> getTrends(Integer days) {
		LocalDateTime startDate = LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0);
		List<DailyTrendVO> normalTrends = orderMapper.getDailyTrends(startDate);
		List<DailyTrendVO> seckillTrends = orderMapper.getSeckillDailyTrends(startDate);
		return mergeTrends(normalTrends, seckillTrends);
	}

	private List<DailyTrendVO> mergeTrends(List<DailyTrendVO> normal, List<DailyTrendVO> seckill) {
		Map<String, DailyTrendVO> merged = new java.util.LinkedHashMap<>();
		for (DailyTrendVO t : normal) {
			merged.put(t.getDate(), t);
		}
		for (DailyTrendVO st : seckill) {
			DailyTrendVO existing = merged.get(st.getDate());
			if (existing != null) {
				existing.setOrderCount(existing.getOrderCount() + st.getOrderCount());
				existing.setAmount(existing.getAmount().add(st.getAmount()));
			} else {
				merged.put(st.getDate(), st);
			}
		}
		List<DailyTrendVO> result = new ArrayList<>(merged.values());
		result.sort((a, b) -> a.getDate().compareTo(b.getDate()));
		return result;
	}

	private ActivityStatsVO buildActivityStatsVO(SeckillActivityBriefVO activity, ActivityStatsRaw raw) {
		ActivityStatsVO vo = new ActivityStatsVO();
		vo.setActivityId(activity.getActivityId());
		vo.setActivityName(activity.getActivityName());
		vo.setStatus(activity.getStatus());
		vo.setStatusName(getActivityStatusName(activity.getStatus()));
		vo.setStartTime(activity.getStartTime());
		vo.setEndTime(activity.getEndTime());

		if (raw != null) {
			vo.setOrderCount(raw.getOrderCount() != null ? raw.getOrderCount() : 0L);
			vo.setTotalAmount(raw.getTotalAmount() != null ? raw.getTotalAmount() : BigDecimal.ZERO);
			vo.setSoldStock(raw.getSoldStock() != null ? raw.getSoldStock() : 0);
		} else {
			vo.setOrderCount(0L);
			vo.setTotalAmount(BigDecimal.ZERO);
			vo.setSoldStock(0);
		}
		vo.setTotalStock(activity.getTotalStock());
		return vo;
	}

	private String getActivityStatusName(Integer status) {
		if (status == null) return "未知";
		return switch (status) {
			case 0 -> "未开始";
			case 1 -> "进行中";
			case 2 -> "已结束";
			default -> "未知";
		};
	}
}
