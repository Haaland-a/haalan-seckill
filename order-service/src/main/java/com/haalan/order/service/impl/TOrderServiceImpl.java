package com.haalan.order.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.client.UserServiceClient;
import com.haalan.api.domain.dto.BatchDeductStockDTO;
import com.haalan.api.domain.vo.BatchDeductStockResultVO;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.api.domain.vo.UserAddressVO;
import com.haalan.common.domain.PageResult;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.domain.dto.CreateOrderRequestDTO;
import com.haalan.order.domain.dto.OrderItemDTO;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.domain.vo.*;
import com.haalan.order.mapper.TOrderMapper;
import com.haalan.order.service.ITOrderItemService;
import com.haalan.order.service.ITOrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单主表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TOrderServiceImpl extends ServiceImpl<TOrderMapper, TOrder> implements ITOrderService {

    private final ITOrderItemService orderItemService;
    private final ItemServiceClient itemServiceClient;
    private final UserServiceClient userServiceClient;

    @Override
    public PageResult<OrderListItemVO> getNormalOrderList(Long userId, Integer pageNum, Integer pageSize, Integer status) {
        // 1. 构建分页对象
        Page<TOrder> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
        LambdaQueryWrapper<TOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TOrder::getUserId, userId);
        if (status != null) {
            queryWrapper.eq(TOrder::getStatus, status);
        }
        queryWrapper.orderByDesc(TOrder::getCreateTime);

        // 3. 执行分页查询
        Page<TOrder> orderPage = this.page(page, queryWrapper);

        // 4. 转换为VO
        List<OrderListItemVO> voList = orderPage.getRecords().stream()
                .map(order -> {
                    OrderListItemVO vo = new OrderListItemVO();
                    vo.setOrderNo(order.getOrderNo());
                    vo.setOrderType(order.getOrderType() != null ? order.getOrderType() : 1);
                    vo.setOrderTypeName(getOrderTypeName(vo.getOrderType()));
                    vo.setTotalAmount(order.getTotalAmount());
                    vo.setActualAmount(order.getActualAmount());
                    vo.setDiscountAmount(order.getDiscountAmount());
                    vo.setStatus(order.getStatus());
                    vo.setStatusName(getStatusName(order.getStatus()));
                    vo.setCreateTime(order.getCreateTime());

                    // 从订单商品明细中获取商品信息
                    TOrderItem orderItem = orderItemService.lambdaQuery()
                            .eq(TOrderItem::getOrderNo, order.getOrderNo())
                            .one();
                    if (orderItem != null) {
                        vo.setProductImage(orderItem.getProductImage());
                        vo.setProductName(orderItem.getProductName());
                        vo.setQuantity(orderItem.getQuantity());
                    }

                    return vo;
                })
                .collect(Collectors.toList());

        // 5. 构建分页结果
        PageResult<OrderListItemVO> result = new PageResult<>();
        result.setTotal(orderPage.getTotal());
        result.setPageNum((int) orderPage.getCurrent());
        result.setPageSize((int) orderPage.getSize());
        result.setList(voList);

        return result;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public OrderDetailVO createOrder(Long userId, CreateOrderRequestDTO request) {
        log.info("开始创建普通订单, userId={}, request={}", userId, request);

        // 1. 验证地址
        UserAddressVO address;
        try {
            address = userServiceClient.getUserAddressById(request.getAddressId(), userId);
        } catch (Exception e) {
            log.error("获取地址失败, addressId={}", request.getAddressId(), e);
            throw new BizIllegalException("收货地址无效");
        }

        // 2. 查询SKU信息
        List<OrderItemDTO> items = request.getItems();
        List<SkuDetailVO> skuDetails = new ArrayList<>();
        for (OrderItemDTO item : items) {
            SkuDetailVO sku = itemServiceClient.getSkuDetail(item.getSkuId());
            if (sku == null) {
                throw new BizIllegalException("商品不存在, skuId: " + item.getSkuId());
            }
            if (sku.getStock() < item.getQuantity()) {
                throw new BizIllegalException("商品库存不足, skuId: " + item.getSkuId()
                        + ", 当前库存: " + sku.getStock());
            }
            skuDetails.add(sku);
        }

        // 3. 生成订单号: 时间戳 + 用户ID后4位 + 随机4位
        String orderNo = generateOrderNo(userId);

        // 4. 扣减库存（乐观锁）
        List<BatchDeductStockDTO> stockList = items.stream()
                .map(item -> BatchDeductStockDTO.builder()
                        .skuId(item.getSkuId())
                        .stock(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        List<BatchDeductStockResultVO> deductResults = itemServiceClient.batchDeductStock(stockList);

        // 检查是否全部扣减成功
        for (BatchDeductStockResultVO result : deductResults) {
            if (!result.getSuccess()) {
                log.error("扣减库存失败, skuId={}, reason={}", result.getSkuId(), result.getFailReason());
                throw new BizIllegalException("扣减库存失败: " + result.getFailReason());
            }
        }

        // 5. 计算金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            OrderItemDTO item = items.get(i);
            SkuDetailVO sku = skuDetails.get(i);
            totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // 6. 保存订单
        TOrder order = new TOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setActualAmount(totalAmount);
        order.setOrderType(1); // 普通订单
        order.setStatus(0); // 待支付
        order.setAddressId(request.getAddressId());
        order.setRemark(request.getRemark());
        order.setCreateTime(LocalDateTime.now());
        this.save(order);

        // 7. 保存订单明细
        List<TOrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OrderItemDTO item = items.get(i);
            SkuDetailVO sku = skuDetails.get(i);

            TOrderItem orderItem = new TOrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(orderNo);
            orderItem.setSkuId(item.getSkuId());
            orderItem.setSkuCode(sku.getSkuCode());
            orderItem.setProductName(sku.getSkuName());
            String images = sku.getImages();
            orderItem.setProductImage(StrUtil.isNotBlank(images) ? images.split(",")[0] : null);
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setTotalPrice(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            Map<String, String> specs = JSONUtil.toBean(sku.getSpecifications(), Map.class);
            orderItem.setSpecifications(specs != null ? JSONUtil.toJsonStr(specs) : null);
            orderItem.setCreateTime(LocalDateTime.now());
            orderItems.add(orderItem);
        }
        orderItemService.saveBatch(orderItems);

        log.info("普通订单创建成功, orderNo={}, totalAmount={}", orderNo, totalAmount);

        // 8. 构建返回VO
        return buildOrderDetailVO(order, orderItems, address);
    }

    @Override
    public OrderDetailVO getOrderDetail(String orderNo, Long userId) {
        log.info("查询普通订单详情, orderNo={}, userId={}", orderNo, userId);

        // 1. 查询订单
        TOrder order = this.lambdaQuery()
                .eq(TOrder::getOrderNo, orderNo)
                .eq(TOrder::getUserId, userId)
                .one();
        if (order == null) {
            throw new BizIllegalException("订单不存在");
        }

        // 2. 查询订单明细
        List<TOrderItem> orderItems = orderItemService.getByOrderNo(orderNo);

        // 3. 获取地址信息
        UserAddressVO address = null;
        try {
            address = userServiceClient.getUserAddressById(order.getAddressId(), userId);
        } catch (Exception e) {
            log.warn("获取地址信息失败, addressId={}", order.getAddressId(), e);
        }

        return buildOrderDetailVO(order, orderItems, address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelOrderResponseVO cancelOrder(String orderNo, Long userId, String cancelReason) {
        log.info("取消普通订单, orderNo={}, userId={}, reason={}", orderNo, userId, cancelReason);

        // 1. 查询订单
        TOrder order = this.lambdaQuery()
                .eq(TOrder::getOrderNo, orderNo)
                .eq(TOrder::getUserId, userId)
                .one();
        if (order == null) {
            throw new BizIllegalException("订单不存在");
        }

        // 2. 校验状态（仅待支付可取消）
        if (order.getStatus() != 0) {
            throw new BizIllegalException("当前订单状态不允许取消, status=" + order.getStatus());
        }

        // 3. 乐观锁更新订单状态: 仅当 status=0 时更新为 status=4
        // 防止并发重复取消
        boolean updated = this.lambdaUpdate()
                .eq(TOrder::getOrderNo, orderNo)
                .eq(TOrder::getStatus, 0) // 乐观锁: 仅待支付可取消
                .set(TOrder::getStatus, 4)
                .set(order.getRemark() != null, TOrder::getRemark,
                        order.getRemark() + " | 取消原因: " + cancelReason)
                .update();

        if (!updated) {
            throw new BizIllegalException("订单取消失败，订单状态已变更");
        }

        // 4. 恢复库存
        List<TOrderItem> orderItems = orderItemService.getByOrderNo(orderNo);
        for (TOrderItem item : orderItems) {
            try {
                Boolean success = itemServiceClient.addStock(item.getSkuId(), item.getQuantity());
                if (!success) {
                    log.error("恢复库存失败, skuId={}, quantity={}", item.getSkuId(), item.getQuantity());
                }
            } catch (Exception e) {
                log.error("调用恢复库存接口异常, skuId={}", item.getSkuId(), e);
            }
        }

        log.info("普通订单取消成功, orderNo={}", orderNo);

        return CancelOrderResponseVO.builder()
                .orderNo(orderNo)
                .status(4)
                .statusName("已取消")
                .build();
    }

    /**
     * 构建订单详情VO
     */
    private OrderDetailVO buildOrderDetailVO(TOrder order, List<TOrderItem> orderItems, UserAddressVO address) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setOrderType(order.getOrderType());
        vo.setOrderTypeName(getOrderTypeName(order.getOrderType()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setActualAmount(order.getActualAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusName(getStatusName(order.getStatus()));
        vo.setCreateTime(order.getCreateTime());

        // 支付过期时间: 创建时间 + 15分钟
        if (order.getCreateTime() != null) {
            LocalDateTime expireTime = order.getCreateTime().plusMinutes(15);
            vo.setPayExpireTime(expireTime);
            vo.setRemainingSeconds(java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds());
        }

        // 订单商品列表
        List<OrderItemVO> itemVOs = orderItems.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setSkuId(item.getSkuId());
            itemVO.setProductName(item.getProductName());
            itemVO.setProductImage(item.getProductImage());
            itemVO.setPrice(item.getPrice());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setTotalPrice(item.getTotalPrice());
            if (StrUtil.isNotBlank(item.getSpecifications())) {
                itemVO.setSpecifications(JSONUtil.toBean(item.getSpecifications(), Map.class));
            }
            return itemVO;
        }).collect(Collectors.toList());
        vo.setOrderItems(itemVOs);

        // 地址信息
        if (address != null) {
            AddressInfoVO addressVO = new AddressInfoVO();
            addressVO.setReceiverName(address.getReceiverName());
            addressVO.setReceiverPhone(address.getReceiverPhone());
            addressVO.setProvince(address.getProvince());
            addressVO.setCity(address.getCity());
            addressVO.setDistrict(address.getDistrict());
            addressVO.setDetailAddress(address.getDetailAddress());
            addressVO.setFullAddress(address.getFullAddress());
            vo.setAddressInfo(addressVO);
        }

        return vo;
    }

    /**
     * 生成订单号: 时间戳(14位) + 用户ID后4位 + 随机4位
     */
    private String generateOrderNo(Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String userIdSuffix = String.format("%04d", userId % 10000);
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        return "N" + timestamp + userIdSuffix + randomSuffix;
    }

    /**
     * 获取订单类型名称
     */
    private String getOrderTypeName(Integer orderType) {
        if (orderType == null) {
            return "普通订单";
        }
        switch (orderType) {
            case 1:
                return "普通订单";
            case 2:
                return "秒杀订单";
            case 3:
                return "团购订单";
            default:
                return "普通订单";
        }
    }

    /**
     * 获取订单状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "待支付";
            case 1:
                return "已支付";
            case 2:
                return "已发货";
            case 3:
                return "已完成";
            case 4:
                return "已取消";
            default:
                return "未知";
        }
    }
}
