package com.haalan.search.domain.dto;

import com.haalan.common.domain.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "ProductSearchDTO", description = "商品搜索参数")
public class ProductSearchDTO extends PageQuery {

	@ApiModelProperty(value = "关键词")
	private String keyword;

	@ApiModelProperty(value = "分类ID")
	private Long categoryId;

	@ApiModelProperty(value = "排序方式: price_asc-价格升序, price_desc-价格降序, sales_desc-销量降序")
	private String sort;
}
