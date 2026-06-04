package com.haalan.item.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.domain.PageDTO;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.common.utils.BeanUtils;
import com.haalan.item.domain.dto.SpuCreateDTO;
import com.haalan.item.domain.dto.SpuQueryDTO;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.SpuCreateResultVO;
import com.haalan.item.domain.vo.SpuListVO;
import com.haalan.item.mapper.TSpuMapper;
import com.haalan.item.service.ITBrandService;
import com.haalan.item.service.ITCategoryService;
import com.haalan.item.service.ITSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

;

/**
 * <p>
 * 商品SPU表 服务实现类
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TSpuServiceImpl extends ServiceImpl<TSpuMapper, TSpu> implements ITSpuService {

	private final ITCategoryService categoryService;
	private final ITBrandService brandService;
	private final RestHighLevelClient restHighLevelClient;


	/**
	 * <p>
	 * 创建spu
	 * </p>
	 *
	 * @param dto
	 * @return SpuCreateResultVO
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SpuCreateResultVO createSpu(SpuCreateDTO dto) {
		TSpu existingSpu = this.lambdaQuery()
				.eq(TSpu::getSpuCode, dto.getSpuCode())
				.one();
		if (existingSpu != null) {
			throw new BizIllegalException("SPU编码已存在");
		}

		TSpu spu = new TSpu();
		spu.setSpuCode(dto.getSpuCode());
		spu.setName(dto.getName());
		spu.setCategoryId(dto.getCategoryId());
		spu.setBrandId(dto.getBrandId());
		spu.setDescription(dto.getDescription());
		spu.setMainImage(dto.getMainImage());

		//用hutool 转成将字符串转成json
		if (!CollectionUtils.isEmpty(dto.getImages())) {
			spu.setImages(JSONUtil.toJsonStr(dto.getImages()));
		}

		spu.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);

		this.save(spu);  // 保存SPU

		return SpuCreateResultVO.builder()
				.spuId(spu.getId())
				.build();  // 返回创建结果
	}

	/**
	 * <p>
	 * 查询spu列表
	 * </p>
	 *
	 * @param queryDTO
	 * @return PageDTO<SpuListVO>
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@Override
	public PageDTO<SpuListVO> querySpuList(SpuQueryDTO queryDTO) {
		LambdaQueryWrapper<TSpu> queryWrapper = new LambdaQueryWrapper<>();   // 创建查询条件

		if (queryDTO.getCategoryId() != null) {
			queryWrapper.eq(TSpu::getCategoryId, queryDTO.getCategoryId());   // 按分类ID查询
		}

		if (StringUtils.hasText(queryDTO.getKeyword())) {   //这个kayword可能是商品名称或者商品编码
			queryWrapper.and(wrapper -> wrapper
					.like(TSpu::getName, queryDTO.getKeyword())
					.or()
					.like(TSpu::getSpuCode, queryDTO.getKeyword()));
		}

		if (queryDTO.getStatus() != null) {
			queryWrapper.eq(TSpu::getStatus, queryDTO.getStatus());
		}

		queryWrapper.orderByDesc(TSpu::getCreateTime);   // 按照创建时间降序

		Page<TSpu> page = this.page(queryDTO.toMpPage(), queryWrapper);   // 分页查询 queryDTO.toMpPage()是查询参数  , queryWrapper是查询条件

		List<Long> categoryIds = page.getRecords().stream()
				.map(TSpu::getCategoryId)
				.distinct()
				.collect(Collectors.toList());

		List<Long> brandIds = page.getRecords().stream()
				.map(TSpu::getBrandId)
				.distinct()
				.collect(Collectors.toList());

		Map<Long, String> categoryMap = new HashMap<>();
		if (!categoryIds.isEmpty()) {
			List<TCategory> categories = categoryService.listByIds(categoryIds);
			categoryMap = categories.stream()
					.collect(Collectors.toMap(TCategory::getId, TCategory::getName));   // 将分类ID列表转换为Map
		}

		Map<Long, String> brandMap = new HashMap<>();
		if (!brandIds.isEmpty()) {
			List<TBrand> brands = brandService.listByIds(brandIds);
			brandMap = brands.stream()
					.collect(Collectors.toMap(TBrand::getId, TBrand::getName));
		}            // 将品牌ID列表转换为Map

		Map<Long, String> finalCategoryMap = categoryMap;  // 创建一个不可修改的Map
		Map<Long, String> finalBrandMap = brandMap;            // 创建一个不可修改的Map
		List<SpuListVO> voList = page.getRecords().stream()
				.map(spu -> {
					SpuListVO vo = new SpuListVO();
					BeanUtils.copyProperties(spu, vo);
					vo.setSpuId(spu.getId());
					vo.setCategoryName(finalCategoryMap.getOrDefault(spu.getCategoryId(), "未知分类"));   // 将分类ID转换为分类名称
					vo.setBrandName(finalBrandMap.getOrDefault(spu.getBrandId(), "未知品牌"));    // 将品牌ID转换为品牌名称
					vo.setStatusName(spu.getStatus() == 1 ? "上架" : "下架");
					return vo;
				})
				.collect(Collectors.toList());

		return PageDTO.of(page, voList);
	}


	/**
	 * <p>
	 * 更新 SPU 状态（上架/下架）
	 * </p>
	 *
	 * @param spuId  SPU ID
	 * @param status 0-下架 1-上架
	 * @author Haaland
	 * @date 2026/6/4
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateSpuStatus(Long spuId, Integer status) {
		TSpu spu = this.getById(spuId);
		if (spu == null) {
			throw new BizIllegalException("SPU不存在");
		}
		spu.setStatus(status);
		this.updateById(spu);
		log.info("SPU {} 状态已更新为 {}", spuId, status == 1 ? "上架" : "下架");
	}


	/**
	 * <p>
	 * 从 ES 删除 SPU
	 * </p>
	 *
	 * @param spuId SPU ID
	 * @author Haaland
	 * @date 2026/4/16
	 */
	public void deleteSpuFromEs(Long spuId) {
		try {
			DeleteRequest request = new DeleteRequest("products")
					.id(String.valueOf(spuId));
			restHighLevelClient.delete(request, RequestOptions.DEFAULT);
			log.info("成功从 ES 删除 SPU {}", spuId);
		} catch (IOException e) {
			log.error("从 ES 删除 SPU {} 失败", spuId, e);
		}
	}
}
