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
import com.haalan.item.domain.dto.SpuUpdateDTO;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.SpuCreateResultVO;
import com.haalan.item.domain.vo.SpuListVO;
import com.haalan.item.mapper.TSkuMapper;
import com.haalan.item.mapper.TSpuMapper;
import com.haalan.item.service.ITBrandService;
import com.haalan.item.service.ITCategoryService;
import com.haalan.item.service.ITSpuService;
import com.haalan.item.service.ProductIndexService;
import com.haalan.item.util.ItemCacheEmptyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
	private final TSkuMapper skuMapper;
	/**
	 * ES 索引统一出入口。
	 * 注意这里注入 Mapper 而不是 ITSkuService：TSkuServiceImpl 已经注入了 ITSpuService（SKU 变更要重建 SPU 文档），
	 * 若这里再注入 ITSkuService 就形成 TSpu→TSku→TSpu 的构造器循环依赖，启动时报 BeanCurrentlyInCreationException。
	 */
	private final ProductIndexService productIndexService;
	private final ItemCacheEmptyUtil itemCacheEmptyUtil;


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
		spu.setCreateTime(LocalDateTime.now());
		spu.setUpdateTime(LocalDateTime.now());
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
	 * <p>
	 * 修复了一处数据不一致：原来这里只改 SPU 自己的 status 就返回了，
	 * 而用户端 {@code queryProductDetailFromDb} 是按 {@code TSku::getStatus == 1} 过滤 SKU 的
	 * （SKU 的 status 和 SPU 的 status 是两套独立字段）。
	 * 结果是管理端点了「下架」，SPU status=0 了，但旗下 SKU 还是 status=1，
	 * 用户照样能在详情页看到并买这些 SKU。
	 * </p>
	 *
	 * <p>
	 * 现在的语义（对齐常见电商口径）：
	 * </p>
	 * <ul>
	 *   <li>SPU 下架（0）：级联把旗下所有 SKU 置为禁用（0），并删除 ES 文档、清详情缓存。</li>
	 *   <li>SPU 上架（1）：<b>不</b>自动把 SKU 都拉起来 —— SKU 可能被单独下架过，
	 *       盲目全量上架会把那些 SKU 也放出去。这里只按「当前仍上架的 SKU」重建 ES 文档。</li>
	 * </ul>
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
		spu.setUpdateTime(LocalDateTime.now());
		this.updateById(spu);

		// 查出该 SPU 下的全部 SKU（不论状态），后面既要级联改状态，也要按 skuId 清缓存
		List<TSku> allSkus = skuMapper.selectList(new LambdaQueryWrapper<TSku>()
				.eq(TSku::getSpuId, spuId));

		if (status != null && status == 0) {
			// 下架：级联禁用旗下所有 SKU
			if (allSkus != null && !allSkus.isEmpty()) {
				skuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TSku>()
						.eq(TSku::getSpuId, spuId)
						.set(TSku::getStatus, 0));
				log.info("SPU {} 下架，已级联禁用旗下 {} 个 SKU", spuId, allSkus.size());
			}
		}

		// 失效缓存：SPU 详情 + 旗下所有 SKU 的详情/库存/销量缓存。
		// 这一步原来完全没有 —— Redis 里 product:detail:{spuId} 的 TTL 是 300 秒，
		// 管理端改完状态后用户端最长还会读到 5 分钟前的旧数据。
		List<Long> skuIds = new ArrayList<>();
		if (allSkus != null) {
			for (TSku sku : allSkus) {
				skuIds.add(sku.getId());
			}
		}
		itemCacheEmptyUtil.invalidateProductRelatedCache(spuId, skuIds);

		// 同步 ES：ProductIndexService 内部会判断「SPU 下架 / 无上架 SKU → 删除文档」
		productIndexService.syncSpuToEs(spuId);

		log.info("SPU {} 状态已更新为 {}", spuId, status == 1 ? "上架" : "下架");
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateSpu(Long spuId, SpuUpdateDTO dto) {
		TSpu spu = this.getById(spuId);
		if (spu == null) {
			throw new BizIllegalException("SPU不存在");
		}
		spu.setName(dto.getName());
		spu.setCategoryId(dto.getCategoryId());
		spu.setBrandId(dto.getBrandId());
		spu.setDescription(dto.getDescription());
		spu.setMainImage(dto.getMainImage());
		if (dto.getImages() != null && !dto.getImages().isEmpty()) {
			spu.setImages(JSONUtil.toJsonStr(dto.getImages()));
		}
		if (dto.getStatus() != null) {
			spu.setStatus(dto.getStatus());
		}
		spu.setUpdateTime(LocalDateTime.now());
		this.updateById(spu);

		// 原来改完 SPU 信息既不清缓存也不重建 ES，导致管理端改了标题/主图，
		// 用户端详情页和搜索结果最长 5 分钟还是旧内容（若改了 status 甚至一直不一致）。
		itemCacheEmptyUtil.deleteCacheBatch(java.util.Collections.singletonList(
				ItemCacheEmptyUtil.productDetailKey(spuId)));
		productIndexService.syncSpuToEs(spuId);

		log.info("SPU {} 信息已更新", spuId);
	}


	/**
	 * <p>
	 * 从 ES 删除 SPU
	 * </p>
	 *
	 * <p>
	 * 已下沉到 {@link ProductIndexService}，这里保留转发以兼容旧调用方。
	 * </p>
	 *
	 * @param spuId SPU ID
	 * @author Haaland
	 * @date 2026/4/16
	 */
	public void deleteSpuFromEs(Long spuId) {
		productIndexService.deleteSpuFromEs(spuId);
	}
}
