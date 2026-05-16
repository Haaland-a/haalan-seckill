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
	 * @param id 分类ID，默认为0表示从根节点开始
	 * @param level 获取的层级数，如果超过最大层级则返回所有层级
	 * @return 分类树列表
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@Override
	public List<CategoryVO> getCategoryTree(Long id, Integer level) {
		LambdaQueryWrapper<TCategory> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(TCategory::getStatus, CategoryStatus.NORMAL.getCode())  //正常状态
				.orderByAsc(TCategory::getSort)    //排序
				.orderByAsc(TCategory::getId); //id升序

		List<TCategory> allCategories = this.list(queryWrapper); //查询所有正常状态的分类  this本impl的实例

		// 如果id为0或null，从根节点开始；否则从指定分类开始
		Long parentId = (id == null || id == 0) ? 0L : id;

		// 如果level为null或小于1，默认返回3层；如果level过大，会自动返回所有可用层级
		int maxLevel = (level == null || level < 1) ? 3 : level;

		return buildTree(allCategories, parentId, 1, maxLevel);
	}

	/**
	 * 递归构建分类树
	 *
	 * @param allCategories 所有分类列表
	 * @param parentId      父分类ID
	 * @param currentLevel  当前层级
	 * @param maxLevel      最大层级
	 * @return 分类树列表
	 */
	private List<CategoryVO> buildTree(List<TCategory> allCategories, Long parentId, int currentLevel, int maxLevel) {
		// 如果当前层级超过最大层级，返回空列表
		if (currentLevel > maxLevel) {
			return List.of();
		}

		return allCategories.stream()
				// 筛选出当前父级下的所有子级分类
				.filter(category -> category.getParentId().equals(parentId))
				// 将分类转换为VO
				.map(category -> {
					CategoryVO vo = new CategoryVO();
					BeanUtils.copyProperties(category, vo);
					vo.setCategoryId(category.getId());

					// 递归构建子级分类，层级+1
					List<CategoryVO> children = buildTree(allCategories, category.getId(), currentLevel + 1, maxLevel);
					if (!children.isEmpty()) {
						vo.setChildren(children);
					}

					return vo;
				})
				.collect(Collectors.toList());
	}
}
