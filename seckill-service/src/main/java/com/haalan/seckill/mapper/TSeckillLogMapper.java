package com.haalan.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.seckill.domain.po.TSeckillLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 秒杀日志表 Mapper 接口
 * </p>
 *
 * @author haaland
 * @since 2026-05-12
 */
@Mapper
public interface TSeckillLogMapper extends BaseMapper<TSeckillLog> {

}
