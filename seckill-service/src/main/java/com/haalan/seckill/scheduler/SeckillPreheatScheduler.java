package com.haalan.seckill.scheduler;

import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.service.ITSeckillPreheatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillPreheatScheduler {

	private final ITSeckillActivityService activityService;
	private final ITSeckillPreheatService preheatService;

	@Scheduled(fixedRate = 600000)
	public void autoPreheat() {
		log.info("自动预热调度开始检查...");
		try {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime twelveHoursLater = now.plusHours(12);

			List<TSeckillActivity> upcomingActivities = activityService.lambdaQuery()
					.eq(TSeckillActivity::getStatus, 0)
					.ge(TSeckillActivity::getStartTime, now)
					.le(TSeckillActivity::getStartTime, twelveHoursLater)
					.list();

			if (upcomingActivities.isEmpty()) {
				log.debug("没有需要预热的即将开始活动");
				return;
			}

			for (TSeckillActivity activity : upcomingActivities) {
				try {
					log.info("自动预热活动: id={}, name={}, startTime={}",
							activity.getId(), activity.getActivityName(), activity.getStartTime());
					preheatService.preheatActivity(activity.getId(), false);
				} catch (Exception e) {
					log.error("自动预热活动失败: id={}, name={}",
							activity.getId(), activity.getActivityName(), e);
				}
			}
			log.info("自动预热调度完成，成功处理 {} 个活动", upcomingActivities.size());
		} catch (Exception e) {
			log.error("自动预热调度异常", e);
		}
	}
}
