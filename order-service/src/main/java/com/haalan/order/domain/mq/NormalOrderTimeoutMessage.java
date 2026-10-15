package com.haalan.order.domain.mq;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 普通订单下单信息（用于发送超时消息，避免监听器中反查数据库）
 * @author Haaland
 * @description NormalOrderTimeoutMessage
 * </p>
 * @date 2026/5/28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@ApiModel("普通订单超时消息")
public class NormalOrderTimeoutMessage {

    @NotNull(message = "订单号不能为空")
    @ApiModelProperty(value = "订单号", required = true)
    private String orderNo;

    @NotNull(message = "用户ID不能为空")
    @ApiModelProperty(value = "用户ID", required = true)
    private Long userId;

    @ApiModelProperty(value = "订单明细(用于超时回滚库存)")
    private List<NormalOrderTimeoutItem> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    @ApiModel("普通订单超时消息明细")
    public static class NormalOrderTimeoutItem {

        @NotNull(message = "skuId不能为空")
        @ApiModelProperty(value = "SKU ID", required = true)
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @ApiModelProperty(value = "购买数量", required = true)
        private Integer quantity;
    }
}
