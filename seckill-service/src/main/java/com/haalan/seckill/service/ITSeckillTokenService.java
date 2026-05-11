package com.haalan.seckill.service;


import com.haalan.seckill.domain.dto.SeckillTokenRequestDTO;
import com.haalan.seckill.domain.vo.SeckillTokenVO;

public interface ITSeckillTokenService {

	SeckillTokenVO generateToken(Long userId, SeckillTokenRequestDTO requestDTO);
}
