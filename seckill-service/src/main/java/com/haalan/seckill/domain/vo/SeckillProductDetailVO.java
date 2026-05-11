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
@ApiModel(value = "SeckillProductDetailVO", description = "秒杀商品详情")
public class SeckillProductDetailVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "秒杀商品ID")
	private Long id;

	@ApiModelProperty(value = "SKU ID")
	private Long skuId;

	@ApiModelProperty(value = "SKU编码")
	private String skuCode;

	@ApiModelProperty(value = "商品名称")
	private String name;

	@ApiModelProperty(value = "商品图片")
	private List<String> images;

	@ApiModelProperty(value = "原价")
	private BigDecimal originalPrice;

	@ApiModelProperty(value = "秒杀价")
	private BigDecimal seckillPrice;

	@ApiModelProperty(value = "库存(从Redis读取)")
	private String stock;

	@ApiModelProperty(value = "已售数量")
	private String soldCount;

	@ApiModelProperty(value = "每人限购")
	private String limitPerUser;

	@ApiModelProperty(value = "用户是否可购买")
	private Boolean userCanBuy;

	@ApiModelProperty(value = "开始时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime startTime;

	@ApiModelProperty(value = "结束时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime endTime;

	@ApiModelProperty(value = "状态: 0-未开始 1-进行中 2-已结束")
	private Integer status;

	@ApiModelProperty(value = "规格")
	private Map<String, String> specifications;
}
