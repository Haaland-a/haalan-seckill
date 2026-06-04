package com.haalan.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.api.domain.vo.SeckillActivityBriefVO;
import com.haalan.seckill.domain.dto.SeckillActivityCreateDTO;
import com.haalan.seckill.domain.dto.SeckillActivityUpdateDTO;
import com.haalan.seckill.domain.po.TSeckillActivity;
import com.haalan.seckill.domain.vo.SeckillActivityCacheVO;
import com.haalan.seckill.domain.vo.SeckillActivityCreateResultVO;
import com.haalan.seckill.domain.vo.SeckillActivityDetailVO;
import com.haalan.seckill.domain.vo.SeckillProductInfoVO;

import java.util.List;

public interface ITSeckillActivityService extends IService<TSeckillActivity> {

	SeckillActivityCreateResultVO createActivity(SeckillActivityCreateDTO dto);

	void updateActivity(Long id, SeckillActivityUpdateDTO dto);

	SeckillActivityUpdateDTO echoActivity(Long id);

	List<SeckillActivityCacheVO> getActivityList(Integer status);

	SeckillActivityDetailVO getActivityDetail(Long activityId);

	SeckillProductInfoVO getProductDetail(Long seckillProductId, Long activityId);

	List<SeckillActivityBriefVO> getAllActivities();
}
