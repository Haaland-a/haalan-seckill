package com.haalan.seckill.service;

import com.haalan.seckill.domain.dto.SeckillExecuteRequestDTO;
import com.haalan.seckill.domain.vo.SeckillExecuteResultVO;

public interface ISeckillExecuteService {

	SeckillExecuteResultVO execute(Long userId, SeckillExecuteRequestDTO request);
}
