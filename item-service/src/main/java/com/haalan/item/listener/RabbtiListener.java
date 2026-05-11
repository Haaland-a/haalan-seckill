package com.haalan.item.listener;

import com.haalan.item.service.ITSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * <p>
 *
 * @author Haaland
 * @description RabbtiListener
 * </p>
 * @date 2026/4/24
 */
@Component  //需要这个,否则不会生效
@RequiredArgsConstructor
public class RabbtiListener {
	private final ITSkuService skuService;

//	@RabbitListener(bindings = @QueueBinding(
//			value = @Queue(name = "seckill.add"),
//			exchange = @Exchange(name = "seckill.exchange", type = ExchangeTypes.DIRECT),
//			key = {"add"}
//	))
//	public void addProduct(TSeckillProduct msg){
//		System.out.println("消费者1接收到的消息：【" + msg + "】");
//		Long skuId = msg.getSkuId();
//		TSku tSku = skuService.getById(skuId);
//	}
}
