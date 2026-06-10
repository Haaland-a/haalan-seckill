package com.haalan.api.client.fallback;

import com.haalan.api.client.SeckillServiceClient;
import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SeckillServiceClientFallback implements FallbackFactory<SeckillServiceClient> {

	@Override
	public SeckillServiceClient create(Throwable cause) {
		return new SeckillServiceClient() {

			@Override
			public List<SeckillActivityBriefVO> getAllActivities() {
				log.error("调用 seckill-service 获取活动列表失败", cause);
				return List.of();
			}
		};
	}
}
