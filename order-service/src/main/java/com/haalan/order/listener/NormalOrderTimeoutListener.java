package com.haalan.order.listener;

import com.haalan.api.client.ItemServiceClient;
import com.haalan.common.utils.UserContext;
import com.haalan.order.config.RabbitConstants;
import com.haalan.order.domain.mq.NormalOrderTimeoutMessage;
import com.haalan.order.domain.po.TOrder;
import com.haalan.order.domain.po.TOrderItem;
import com.haalan.order.service.ITOrderItemService;
import com.haalan.order.service.ITOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class NormalOrderTimeoutListener {

	private final ITOrderService orderService;

	private final ITOrderItemService orderItemService;

	private final ItemServiceClient itemServiceClient;

	/**
	 * 监听普通订单超时死信队列 - 超时未支付的普通订单会自动到达这里
	 */
	@RabbitListener(queues = RabbitConstants.NORMAL_ORDER_TIMEOUT_DLX_QUEUE)
	@Transactional(rollbackFor = Exception.class)
	public void handleTimeoutOrder(NormalOrderTimeoutMessage message) {
		log.info("消费者接收到普通订单超时消息：【{}】", message);

		UserContext.setUser(message.getUserId());

		try {
			// 乐观锁取消: 只有 status=0 的待支付订单才会被置为 4，已支付/已取消则幂等跳过
			boolean cancelled = orderService.lambdaUpdate()
					.eq(TOrder::getOrderNo, message.getOrderNo())
					.eq(TOrder::getStatus, 0)
					.set(TOrder::getStatus, 4)
					.set(TOrder::getCancelTime, LocalDateTime.now())
					.update();

			if (!cancelled) {
				log.info("普通订单非待支付状态，跳过超时取消: orderNo={}", message.getOrderNo());
				return;
			}

			log.info("普通订单超时取消成功，开始回滚库存: orderNo={}", message.getOrderNo());

			// 优先用消息里带的明细；老格式消息没有明细时才反查（消息转换器兼容）
			List<NormalOrderTimeoutMessage.NormalOrderTimeoutItem> items = message.getItems();
			if (items == null || items.isEmpty()) {
				List<TOrderItem> orderItems = orderItemService.getByOrderNo(message.getOrderNo());
				for (TOrderItem item : orderItems) {
					rollbackStock(item.getSkuId(), item.getQuantity(), message.getOrderNo());
				}
			} else {
				for (NormalOrderTimeoutMessage.NormalOrderTimeoutItem item : items) {
					rollbackStock(item.getSkuId(), item.getQuantity(), message.getOrderNo());
				}
			}
		} catch (Exception e) {
			log.error("处理普通订单超时失败: orderNo={}", message.getOrderNo(), e);
			throw e; // 让 MQ 重试，重试耗尽进入备用死信
		} finally {
			// 清理，防止内存泄漏
			UserContext.removeUser();
		}
	}

	/**
	 * 回滚单个 SKU 库存
	 */
	private void rollbackStock(Long skuId, Integer quantity, String orderNo) {
		try {
			Boolean success = itemServiceClient.addStock(skuId, quantity);
			if (!Boolean.TRUE.equals(success)) {
				log.error("恢复库存失败, skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
			} else {
				log.info("恢复库存成功, skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo);
			}
		} catch (Exception e) {
			log.error("调用恢复库存接口异常, skuId={}, quantity={}, orderNo={}", skuId, quantity, orderNo, e);
			throw new RuntimeException("恢复库存失败: skuId=" + skuId, e);
		}
	}
}
