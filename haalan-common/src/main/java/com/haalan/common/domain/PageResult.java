package com.haalan.common.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "PageResult", description = "分页结果")
public class PageResult<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "总记录数")
	private Long total;

	@ApiModelProperty(value = "当前页码")
	private Integer pageNum;

	@ApiModelProperty(value = "每页大小")
	private Integer pageSize;

	@ApiModelProperty(value = "数据列表")
	private List<T> list;
}
