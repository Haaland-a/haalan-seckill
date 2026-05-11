package com.haalan.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.vo.CategoryVO;

import java.util.List;

/**
 * <p>
 * 商品分类表 服务类
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
public interface ITCategoryService extends IService<TCategory> {

	List<CategoryVO> getCategoryTree();
}
