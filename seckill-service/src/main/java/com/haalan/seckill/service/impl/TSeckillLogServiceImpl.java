package com.haalan.seckill.service.impl;

import com.haalan.seckill.domain.po.TSeckillLog;
import com.haalan.seckill.mapper.TSeckillLogMapper;
import com.haalan.seckill.service.ITSeckillLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀日志表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-12
 */
@Service
public class TSeckillLogServiceImpl extends ServiceImpl<TSeckillLogMapper, TSeckillLog> implements ITSeckillLogService {

}
