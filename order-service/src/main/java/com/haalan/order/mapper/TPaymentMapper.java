package com.haalan.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.order.domain.po.TPayment;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 支付表 Mapper 接口
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Mapper
public interface TPaymentMapper extends BaseMapper<TPayment> {

}
