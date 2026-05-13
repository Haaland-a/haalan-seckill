package com.haalan.seckill.service;

import com.haalan.seckill.domain.dto.SeckillExecuteRequestDTO;
import com.haalan.seckill.domain.vo.SeckillExecuteResultVO;
import com.haalan.seckill.domain.vo.SeckillResultVO;

public interface ISeckillExecuteService {

	SeckillExecuteResultVO execute(Long userId, SeckillExecuteRequestDTO request);

	/**
	 * 查询秒杀结果
	 *
	 * @param requestId 请求ID
	 * @return 秒杀结果
	 */
	SeckillResultVO queryResult(String requestId);

}
