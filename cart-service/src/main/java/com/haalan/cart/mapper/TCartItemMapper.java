package com.haalan.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.cart.domain.po.TCartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 购物车明细 Mapper 接口
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Mapper
public interface TCartItemMapper extends BaseMapper<TCartItem> {

}
