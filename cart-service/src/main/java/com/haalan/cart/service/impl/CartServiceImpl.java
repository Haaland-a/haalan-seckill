package com.haalan.cart.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.cart.domain.dto.AddCartItemDTO;
import com.haalan.cart.domain.po.TCartItem;
import com.haalan.cart.domain.vo.CartItemVO;
import com.haalan.cart.domain.vo.CartVO;
import com.haalan.cart.mapper.TCartItemMapper;
import com.haalan.cart.service.ICartService;
import com.haalan.common.exception.BadRequestException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 购物车 Service 实现
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements ICartService {

	/** 购物车项数上限，防止恶意刷单/刷购物车 */
	private static final int MAX_CART_ITEMS = 100;

	/** 角标缓存 key 前缀，TTL 5 分钟 */
	private static final String CART_COUNT_KEY = "cart:count:";
	private static final Duration CART_COUNT_TTL = Duration.ofMinutes(5);

	private final TCartItemMapper cartItemMapper;
	private final ItemServiceClient itemServiceClient;
	private final StringRedisTemplate stringRedisTemplate;

	@Override
	public CartVO getCart(Long userId) {
		requireLogin(userId);
		List<TCartItem> items = listByUser(userId);
		return buildCartVO(items);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Integer addItem(Long userId, AddCartItemDTO dto) {
		requireLogin(userId);
		Long skuId = dto.getSkuId();
		Integer quantity = dto.getQuantity();

		// 1. 校验 SKU 真实存在且上架（远程调用 item-service，内部走缓存空对象，成本很低）
		SkuDetailVO sku = itemServiceClient.getSkuDetail(skuId);
		if (sku == null || sku.getSkuId() == null) {
			throw new BadRequestException("商品不存在");
		}
		if (sku.getStatus() != null && sku.getStatus() == 0) {
			throw new BadRequestException("商品已下架");
		}

		// 2. 查询购物车中是否已有该 SKU
		TCartItem exist = cartItemMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getUserId, userId)
				.eq(TCartItem::getSkuId, skuId));

		int newQuantity = quantity;
		if (exist != null) {
			newQuantity = exist.getQuantity() + quantity;
		} else {
			// 新增前校验购物车种类上限
			Long count = cartItemMapper.selectCount(new LambdaQueryWrapper<TCartItem>()
					.eq(TCartItem::getUserId, userId));
			if (count != null && count >= MAX_CART_ITEMS) {
				throw new BadRequestException("购物车商品种类已达上限 " + MAX_CART_ITEMS);
			}
		}

		// 3. 校验库存（只做提示性校验，真正扣减在下单时用乐观锁保证）
		if (sku.getStock() != null && newQuantity > sku.getStock()) {
			throw new BadRequestException("库存不足，当前仅剩 " + sku.getStock() + " 件");
		}

		LocalDateTime now = LocalDateTime.now();
		if (exist != null) {
			cartItemMapper.update(null, new LambdaUpdateWrapper<TCartItem>()
					.eq(TCartItem::getId, exist.getId())
					.set(TCartItem::getQuantity, newQuantity)
					.set(TCartItem::getSpuId, sku.getSpuId())
					.set(TCartItem::getSelected, 1)
					.set(TCartItem::getUpdateTime, now));
		} else {
			TCartItem item = new TCartItem()
					.setUserId(userId)
					.setSkuId(skuId)
					.setSpuId(sku.getSpuId())
					.setQuantity(newQuantity)
					.setSelected(1)
					.setCreateTime(now)
					.setUpdateTime(now);
			cartItemMapper.insert(item);
		}

		evictCountCache(userId);
		return newQuantity;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateQuantity(Long userId, Long cartItemId, Integer quantity) {
		requireLogin(userId);
		TCartItem item = getOwnedItem(userId, cartItemId);

		// 校验库存上限
		SkuDetailVO sku = itemServiceClient.getSkuDetail(item.getSkuId());
		if (sku == null || sku.getSkuId() == null) {
			throw new BadRequestException("商品已失效");
		}
		if (sku.getStock() != null && quantity > sku.getStock()) {
			throw new BadRequestException("库存不足，当前仅剩 " + sku.getStock() + " 件");
		}

		cartItemMapper.update(null, new LambdaUpdateWrapper<TCartItem>()
				.eq(TCartItem::getId, cartItemId)
				.eq(TCartItem::getUserId, userId)
				.set(TCartItem::getQuantity, quantity)
				.set(TCartItem::getUpdateTime, LocalDateTime.now()));
		evictCountCache(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeItem(Long userId, Long cartItemId) {
		requireLogin(userId);
		getOwnedItem(userId, cartItemId);
		cartItemMapper.delete(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getId, cartItemId)
				.eq(TCartItem::getUserId, userId));
		evictCountCache(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeItems(Long userId, List<Long> cartItemIds) {
		requireLogin(userId);
		if (cartItemIds == null || cartItemIds.isEmpty()) {
			return;
		}
		cartItemMapper.delete(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getUserId, userId)
				.in(TCartItem::getId, cartItemIds));
		evictCountCache(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void clear(Long userId) {
		requireLogin(userId);
		cartItemMapper.delete(new LambdaQueryWrapper<TCartItem>().eq(TCartItem::getUserId, userId));
		evictCountCache(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void select(Long userId, Long cartItemId, Integer selected) {
		requireLogin(userId);
		getOwnedItem(userId, cartItemId);
		cartItemMapper.update(null, new LambdaUpdateWrapper<TCartItem>()
				.eq(TCartItem::getId, cartItemId)
				.eq(TCartItem::getUserId, userId)
				.set(TCartItem::getSelected, normalizeSelected(selected))
				.set(TCartItem::getUpdateTime, LocalDateTime.now()));
		evictCountCache(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void selectAll(Long userId, Integer selected) {
		requireLogin(userId);
		cartItemMapper.update(null, new LambdaUpdateWrapper<TCartItem>()
				.eq(TCartItem::getUserId, userId)
				.set(TCartItem::getSelected, normalizeSelected(selected))
				.set(TCartItem::getUpdateTime, LocalDateTime.now()));
		evictCountCache(userId);
	}

	@Override
	public List<CartItemVO> getSelectedItems(Long userId) {
		requireLogin(userId);
		List<TCartItem> items = listByUser(userId).stream()
				.filter(i -> Integer.valueOf(1).equals(i.getSelected()))
				.collect(Collectors.toList());
		if (items.isEmpty()) {
			return Collections.emptyList();
		}
		return buildCartVO(items).getItems().stream()
				.filter(i -> Boolean.TRUE.equals(i.getValid()))
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeSelected(Long userId) {
		requireLogin(userId);
		cartItemMapper.delete(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getUserId, userId)
				.eq(TCartItem::getSelected, 1));
		evictCountCache(userId);
	}

	@Override
	public Integer getCartCount(Long userId) {
		if (userId == null) {
			return 0;
		}
		String key = CART_COUNT_KEY + userId;
		try {
			String cached = stringRedisTemplate.opsForValue().get(key);
			if (cached != null) {
				return Integer.parseInt(cached);
			}
		} catch (Exception e) {
			// 缓存故障不影响主流程，降级查库
			log.warn("读取购物车角标缓存失败, userId={}", userId, e);
		}

		Integer count = countQuantity(userId);
		try {
			stringRedisTemplate.opsForValue().set(key, String.valueOf(count), CART_COUNT_TTL);
		} catch (Exception e) {
			log.warn("写入购物车角标缓存失败, userId={}", userId, e);
		}
		return count;
	}

	// ==================== 私有方法 ====================

	private Integer countQuantity(Long userId) {
		List<TCartItem> items = listByUser(userId);
		return items.stream()
				.map(TCartItem::getQuantity)
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();
	}

	private List<TCartItem> listByUser(Long userId) {
		return cartItemMapper.selectList(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getUserId, userId)
				.orderByDesc(TCartItem::getUpdateTime)
				.orderByDesc(TCartItem::getId));
	}

	private TCartItem getOwnedItem(Long userId, Long cartItemId) {
		TCartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
				.eq(TCartItem::getId, cartItemId)
				.eq(TCartItem::getUserId, userId));
		if (item == null) {
			throw new BadRequestException("购物车商品不存在");
		}
		return item;
	}

	private Integer normalizeSelected(Integer selected) {
		return Integer.valueOf(1).equals(selected) ? 1 : 0;
	}

	private void requireLogin(Long userId) {
		if (userId == null) {
			throw new BadRequestException("请先登录");
		}
	}

	private void evictCountCache(Long userId) {
		try {
			stringRedisTemplate.delete(CART_COUNT_KEY + userId);
		} catch (Exception e) {
			log.warn("清除购物车角标缓存失败, userId={}", userId, e);
		}
	}

	/**
	 * 把购物车行 + 实时 SKU 信息组装成 VO。
	 * 一次性批量调用 item-service，避免 N+1 远程调用。
	 */
	private CartVO buildCartVO(List<TCartItem> items) {
		CartVO vo = new CartVO();
		List<CartItemVO> itemVOs = new ArrayList<>(items.size());
		BigDecimal selectedAmount = BigDecimal.ZERO;
		int totalCount = 0;
		int totalQuantity = 0;

		Map<Long, SkuDetailVO> skuMap = batchLoadSkus(items);

		for (TCartItem item : items) {
			CartItemVO itemVO = new CartItemVO();
			itemVO.setId(item.getId());
			itemVO.setSkuId(item.getSkuId());
			itemVO.setSpuId(item.getSpuId());
			itemVO.setQuantity(item.getQuantity());
			itemVO.setSelected(item.getSelected() == null ? 0 : item.getSelected());

			SkuDetailVO sku = skuMap.get(item.getSkuId());
			if (sku == null || sku.getSkuId() == null) {
				itemVO.setValid(false);
				itemVO.setInvalidReason("商品已失效");
				itemVO.setProductName("商品已失效");
				itemVO.setPrice(BigDecimal.ZERO);
				itemVO.setTotalPrice(BigDecimal.ZERO);
			} else if (sku.getStatus() != null && sku.getStatus() == 0) {
				itemVO.setValid(false);
				itemVO.setInvalidReason("商品已下架");
				fillSkuInfo(itemVO, sku);
			} else if (sku.getStock() == null || sku.getStock() <= 0) {
				itemVO.setValid(false);
				itemVO.setInvalidReason("商品已售罄");
				fillSkuInfo(itemVO, sku);
			} else {
				itemVO.setValid(true);
				fillSkuInfo(itemVO, sku);
				if (itemVO.getQuantity() != null && itemVO.getQuantity() > sku.getStock()) {
					itemVO.setValid(false);
					itemVO.setInvalidReason("库存不足，仅剩 " + sku.getStock() + " 件");
				}
			}

			// 统计只算有效商品
			if (Boolean.TRUE.equals(itemVO.getValid())) {
				totalCount++;
				totalQuantity += itemVO.getQuantity() == null ? 0 : itemVO.getQuantity();
				if (Integer.valueOf(1).equals(itemVO.getSelected())) {
					selectedAmount = selectedAmount.add(
							itemVO.getTotalPrice() == null ? BigDecimal.ZERO : itemVO.getTotalPrice());
				}
			}

			itemVOs.add(itemVO);
		}

		vo.setItems(itemVOs);
		vo.setTotalCount(totalCount);
		vo.setTotalQuantity(totalQuantity);
		vo.setSelectedAmount(selectedAmount);
		return vo;
	}

	private void fillSkuInfo(CartItemVO itemVO, SkuDetailVO sku) {
		itemVO.setSpuId(sku.getSpuId());
		itemVO.setProductName(sku.getSkuName());
		itemVO.setProductImage(firstImage(sku.getImages()));
		itemVO.setSpecifications(parseSpecifications(sku.getSpecifications()));
		itemVO.setPrice(sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice());
		itemVO.setStock(sku.getStock());
		itemVO.setTotalPrice(itemVO.getPrice().multiply(
				BigDecimal.valueOf(itemVO.getQuantity() == null ? 0 : itemVO.getQuantity())));
	}

	/**
	 * 批量拉取 SKU 详情，一次远程调用搞定
	 */
	private Map<Long, SkuDetailVO> batchLoadSkus(List<TCartItem> items) {
		List<Long> skuIds = items.stream()
				.map(TCartItem::getSkuId)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		if (skuIds.isEmpty()) {
			return Collections.emptyMap();
		}
		try {
			List<SkuDetailVO> skus = itemServiceClient.getSkuDetailBatch(skuIds);
			if (skus == null || skus.isEmpty()) {
				return Collections.emptyMap();
			}
			return skus.stream()
					.filter(s -> s != null && s.getSkuId() != null)
					.collect(Collectors.toMap(SkuDetailVO::getSkuId, Function.identity(),
							(a, b) -> a, LinkedHashMap::new));
		} catch (Exception e) {
			// 商品服务不可用时，购物车仍然要能打开（展示为失效商品），不能直接 500
			log.error("批量查询SKU详情失败, skuIds={}", skuIds, e);
			return Collections.emptyMap();
		}
	}

	/**
	 * images 存的是 JSON 数组字符串，取第一张作为列表图
	 */
	private String firstImage(String images) {
		if (StrUtil.isBlank(images)) {
			return null;
		}
		try {
			List<String> list = JSONUtil.toList(images, String.class);
			return list.isEmpty() ? null : list.get(0);
		} catch (Exception e) {
			// 不是 JSON 数组就当普通 URL 处理
			return images;
		}
	}

	/**
	 * specifications 存的是 JSON 对象字符串
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parseSpecifications(String specifications) {
		if (StrUtil.isBlank(specifications)) {
			return Collections.emptyMap();
		}
		try {
			return JSONUtil.toBean(specifications, Map.class);
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}
}
