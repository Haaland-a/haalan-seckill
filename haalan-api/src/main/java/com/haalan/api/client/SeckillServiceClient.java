package com.haalan.api.client;

import com.haalan.api.client.fallback.SeckillServiceClientFallback;
import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(value = "seckill-service", fallbackFactory = SeckillServiceClientFallback.class)
public interface SeckillServiceClient {

	@GetMapping("/api/seckill/activities/all")
	List<SeckillActivityBriefVO> getAllActivities();
}
