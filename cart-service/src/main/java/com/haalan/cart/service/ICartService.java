package com.haalan.cart.service;

import com.haalan.cart.domain.dto.AddCartItemDTO;
import com.haalan.cart.domain.vo.CartItemVO;
import com.haalan.cart.domain.vo.CartVO;

import java.util.List;

/**
 * <p>
 * 购物车 Service 接口
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
public interface ICartService {

	/**
	 * 获取当前用户购物车（含实时商品信息）
	 */
	CartVO getCart(Long userId);

	/**
	 * 加入购物车（已存在则累加数量）
	 *
	 * @return 加入后该 SKU 在购物车中的总数量
	 */
	Integer addItem(Long userId, AddCartItemDTO dto);

	/**
	 * 修改购物车项数量
	 */
	void updateQuantity(Long userId, Long cartItemId, Integer quantity);

	/**
	 * 删除购物车项
	 */
	void removeItem(Long userId, Long cartItemId);

	/**
	 * 批量删除购物车项
	 */
	void removeItems(Long userId, List<Long> cartItemIds);

	/**
	 * 清空购物车
	 */
	void clear(Long userId);

	/**
	 * 勾选/取消勾选单个购物车项
	 */
	void select(Long userId, Long cartItemId, Integer selected);

	/**
	 * 全选/取消全选
	 */
	void selectAll(Long userId, Integer selected);

	/**
	 * 获取已勾选且有效的购物车项（结算用）
	 */
	List<CartItemVO> getSelectedItems(Long userId);

	/**
	 * 下单成功后移除已勾选商品
	 */
	void removeSelected(Long userId);

	/**
	 * 购物车商品件数（用于顶部角标）
	 */
	Integer getCartCount(Long userId);
}
