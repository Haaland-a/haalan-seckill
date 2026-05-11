package com.haalan.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.mapper.TPaymentMapper;
import com.haalan.order.service.ITPaymentService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 支付表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Service
public class TPaymentServiceImpl extends ServiceImpl<TPaymentMapper, TPayment> implements ITPaymentService {

}
