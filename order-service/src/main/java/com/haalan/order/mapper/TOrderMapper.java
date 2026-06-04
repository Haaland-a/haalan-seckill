package com.haalan.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.order.domain.ActivityStatsRaw;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.DailyTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TOrderMapper extends BaseMapper<TOrder> {

	// ==================== 管理统计 ====================

	BigDecimal sumPaidAmount();

	BigDecimal sumTodayAmount();

	Long countToday();

	List<DailyTrendVO> getDailyTrends(@Param("startDate") LocalDateTime startDate);

	// ==================== 秒杀活动统计 ====================

	List<ActivityStatsRaw> getSeckillActivityStatsRaw();

	ActivityStatsRaw getSeckillActivityStatsRawById(@Param("activityId") Long activityId);

	List<DailyTrendVO> getSeckillDailyTrends(@Param("startDate") LocalDateTime startDate);

	// ==================== 秒杀订单查询（显式表名，绕开动态表名拦截器） ====================

	TSeckillOrder getSeckillOrderByNo(@Param("orderNo") String orderNo);

	Long countSeckillOrders(@Param("orderNo") String orderNo,
							@Param("status") Integer status,
							@Param("startDate") String startDate,
							@Param("endDate") String endDate);

	List<TSeckillOrder> pageSeckillOrders(@Param("orderNo") String orderNo,
										  @Param("status") Integer status,
										  @Param("startDate") String startDate,
										  @Param("endDate") String endDate,
										  @Param("offset") int offset,
										  @Param("limit") int limit);
}
