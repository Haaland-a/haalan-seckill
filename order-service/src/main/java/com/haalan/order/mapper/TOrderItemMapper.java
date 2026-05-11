package com.haalan.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.order.domain.po.TOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 订单商品明细表 Mapper 接口
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Mapper
public interface TOrderItemMapper extends BaseMapper<TOrderItem> {

}
