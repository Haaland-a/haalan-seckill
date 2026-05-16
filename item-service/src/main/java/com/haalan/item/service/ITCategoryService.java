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

	/**
	 * 获取分类树
	 *
	 * @param id    分类ID，默认为0表示从根节点开始
	 * @param level 获取的层级数，如果超过最大层级则返回所有层级
	 * @return 分类树列表
	 */
	List<CategoryVO> getCategoryTree(Long id, Integer level);
}
