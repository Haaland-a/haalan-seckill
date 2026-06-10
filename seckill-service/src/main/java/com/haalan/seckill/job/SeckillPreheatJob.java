package com.haalan.seckill.job;

import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.service.ITSeckillPreheatService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillPreheatJob {

	private final ITSeckillActivityService activityService;
	private final ITSeckillPreheatService preheatService;

	/**
	 * 秒杀活动自动预热任务
	 * 在 XXL-JOB 调度中心配置任务，Handler 名称为：seckillPreheatJobHandler
	 */
	@XxlJob("seckillPreheatJobHandler")
	public void autoPreheat() {
		log.info("【XXL-JOB】自动预热调度开始执行...");

		try {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime twelveHoursLater = now.plusHours(12);
			LocalDateTime twelveHoursAgo = now.minusHours(12);

			List<TSeckillActivity> upcomingActivities = activityService.lambdaQuery()
					.eq(TSeckillActivity::getStatus, 0)
					.ge(TSeckillActivity::getStartTime, twelveHoursAgo)
					.le(TSeckillActivity::getStartTime, twelveHoursLater)
					.gt(TSeckillActivity::getEndTime, now)
					.list();

			if (upcomingActivities.isEmpty()) {
				log.info("【XXL-JOB】没有需要处理的未开始活动");
				XxlJobHelper.handleSuccess("没有需要处理的活动");
				return;
			}

			int successCount = 0;
			int failCount = 0;

			for (TSeckillActivity activity : upcomingActivities) {
				try {
					log.info("【XXL-JOB】处理活动: id={}, name={}, startTime={}",
							activity.getId(), activity.getActivityName(), activity.getStartTime());

					preheatService.preheatActivity(activity.getId(), false);
					successCount++;

					// 记录日志到 XXL-JOB 控制台
					XxlJobHelper.log("成功预热活动: id={}, name={}", activity.getId(), activity.getActivityName());

				} catch (Exception e) {
					failCount++;
					log.error("【XXL-JOB】处理活动失败: id={}, name={}", activity.getId(), activity.getActivityName(), e);
					XxlJobHelper.log("预热活动失败: id={}, error={}", activity.getId(), e.getMessage());
				}
			}

			String result = String.format("预热完成，成功: %d, 失败: %d", successCount, failCount);
			log.info("【XXL-JOB】{}", result);
			XxlJobHelper.handleSuccess(result);

		} catch (Exception e) {
			log.error("【XXL-JOB】自动预热调度异常", e);
			XxlJobHelper.handleFail("预热调度异常: " + e.getMessage());
		}
	}
}