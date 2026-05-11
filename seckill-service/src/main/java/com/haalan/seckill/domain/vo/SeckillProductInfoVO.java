package com.haalan.seckill.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SeckillProductInfoVO", description = "秒杀商品详情（用户端）")
public class SeckillProductInfoVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long seckillProductId;

	@ApiModelProperty(value = "活动ID")
	private Long activityId;

	@ApiModelProperty(value = "SKU ID")
	private Long skuId;

	@ApiModelProperty(value = "商品名称")
	private String productName;

	@ApiModelProperty(value = "商品图片列表")
	private List<String> productImages;

	@ApiModelProperty(value = "规格参数")
	private Map<String, String> specifications;

	@ApiModelProperty(value = "原价")
	private BigDecimal originalPrice;

	@ApiModelProperty(value = "秒杀价")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "库存")
	private Integer stock;

	@ApiModelProperty(value = "每人限购数量")
	private String limitPerUser;

	@ApiModelProperty(value = "用户已购买数量")
	private Integer userPurchaseCount;

	@ApiModelProperty(value = "开始时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime startTime;

	@ApiModelProperty(value = "结束时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "倒计时（剩余秒数）")
	private Long countdown;

	@ApiModelProperty(value = "状态: 0-未开始 1-进行中 2-已结束")
	private Integer status;
}
