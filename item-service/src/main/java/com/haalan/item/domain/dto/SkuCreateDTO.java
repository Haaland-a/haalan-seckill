package com.haalan.item.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(value = "SkuCreateDTO", description = "创建SKU请求")
public class SkuCreateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "SKU编码", required = true)
	@NotBlank(message = "SKU编码不能为空")
	private String skuCode;

	@ApiModelProperty(value = "SPU ID", required = true)
	@NotNull(message = "SPU ID不能为空")
	private Long spuId;

	@ApiModelProperty(value = "SKU名称", required = true)
	@NotBlank(message = "SKU名称不能为空")
	private String name;

	@ApiModelProperty(value = "规格参数", required = true)
	@NotNull(message = "规格参数不能为空")
	private Map<String, String> specifications;

	@ApiModelProperty(value = "销售价格", required = true)
	@NotNull(message = "销售价格不能为空")
	private BigDecimal price;

	@ApiModelProperty(value = "促销价格")
	private BigDecimal promotionPrice;

	@ApiModelProperty(value = "库存", required = true)
	@NotNull(message = "库存不能为空")
	private Integer stock;

	@TableField("alipay_product_code")
	private String alipayProductCode;

	@TableField("wechat_product_code")
	private String wechatProductCode;

	@ApiModelProperty(value = "商品图片列表")
	private List<String> images;

	@ApiModelProperty(value = "状态: 0-下架 1-上架")
	private Integer status = 1;
}
