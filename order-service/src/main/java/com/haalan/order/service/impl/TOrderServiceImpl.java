package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.mapper.TOrderMapper;
import com.haalan.order.service.ITOrderService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单主表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Service
public class TOrderServiceImpl extends ServiceImpl<TOrderMapper, TOrder> implements ITOrderService {

}
