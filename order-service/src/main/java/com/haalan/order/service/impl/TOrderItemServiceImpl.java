package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.mapper.TOrderItemMapper;
import com.haalan.order.service.ITOrderItemService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单商品明细表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Service
public class TOrderItemServiceImpl extends ServiceImpl<TOrderItemMapper, TOrderItem> implements ITOrderItemService {

}
