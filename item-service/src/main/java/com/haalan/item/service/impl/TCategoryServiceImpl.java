package com.haalan.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.vo.CategoryVO;
import com.haalan.item.enums.CategoryStatus;
import com.haalan.item.mapper.TCategoryMapper;
import com.haalan.item.service.ITCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TCategoryServiceImpl extends ServiceImpl<TCategoryMapper, TCategory> implements ITCategoryService {

	/**
	 * <p>
	 * 获取分类树
	 * </p>
	 *
	 * @param
	 * @return
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@Override
	public List<CategoryVO> getCategoryTree() {
		LambdaQueryWrapper<TCategory> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(TCategory::getStatus, CategoryStatus.NORMAL.getCode())  //正常状态
				.orderByAsc(TCategory::getSort)    //排序
				.orderByAsc(TCategory::getId); //id升序

		List<TCategory> allCategories = this.list(queryWrapper); //查询所有正常状态的分类  this本impl的实例

		return buildTree(allCategories, 0L);  //树的默认父级id为0L
	}

	private List<CategoryVO> buildTree(List<TCategory> allCategories, Long parentId) {
		return allCategories.stream()
				.filter(category -> category.getParentId().equals(parentId))
				.map(category -> {
					CategoryVO vo = new CategoryVO();
					BeanUtils.copyProperties(category, vo);
					vo.setCategoryId(category.getId());

					List<CategoryVO> children = buildTree(allCategories, category.getId());  //递归构建子级分类
					if (!children.isEmpty()) {
						vo.setChildren(children);
					}

					return vo;
				})
				.collect(Collectors.toList());
	}
}
