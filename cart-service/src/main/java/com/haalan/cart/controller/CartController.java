package com.haalan.cart.controller;

import com.haalan.cart.domain.dto.AddCartItemDTO;
import com.haalan.cart.domain.dto.SelectCartItemDTO;
import com.haalan.cart.domain.dto.UpdateCartItemDTO;
import com.haalan.cart.domain.vo.CartItemVO;
import com.haalan.cart.domain.vo.CartVO;
import com.haalan.cart.service.ICartService;
import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 购物车 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-10-14
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Api(tags = "购物车管理")
public class CartController {

	private final ICartService cartService;

	@GetMapping("/list")
	@ApiOperation("获取购物车列表")
	public R<CartVO> getCart() {
		return R.success(cartService.getCart(UserContext.getUser()));
	}

	@GetMapping("/count")
	@ApiOperation("获取购物车商品件数（顶部角标）")
	public R<Integer> getCartCount() {
		return R.success(cartService.getCartCount(UserContext.getUser()));
	}

	@PostMapping("/add")
	@ApiOperation("加入购物车")
	public R<Integer> addItem(@RequestBody @Validated AddCartItemDTO dto) {
		Integer quantity = cartService.addItem(UserContext.getUser(), dto);
		return R.success("已加入购物车", quantity);
	}

	@PutMapping("/{cartItemId}/quantity")
	@ApiOperation("修改购物车商品数量")
	public R<Void> updateQuantity(@PathVariable Long cartItemId,
								  @RequestBody @Validated UpdateCartItemDTO dto) {
		cartService.updateQuantity(UserContext.getUser(), cartItemId, dto.getQuantity());
		return R.success("修改成功");
	}

	@DeleteMapping("/{cartItemId}")
	@ApiOperation("删除购物车商品")
	public R<Void> removeItem(@PathVariable Long cartItemId) {
		cartService.removeItem(UserContext.getUser(), cartItemId);
		return R.success("删除成功");
	}

	@DeleteMapping("/batch")
	@ApiOperation("批量删除购物车商品")
	public R<Void> removeItems(@RequestBody List<Long> cartItemIds) {
		cartService.removeItems(UserContext.getUser(), cartItemIds);
		return R.success("删除成功");
	}

	@DeleteMapping("/clear")
	@ApiOperation("清空购物车")
	public R<Void> clear() {
		cartService.clear(UserContext.getUser());
		return R.success("已清空购物车");
	}

	@PutMapping("/{cartItemId}/selected")
	@ApiOperation("勾选/取消勾选购物车商品")
	public R<Void> select(@PathVariable Long cartItemId,
						  @RequestBody @Validated SelectCartItemDTO dto) {
		cartService.select(UserContext.getUser(), cartItemId, dto.getSelected());
		return R.success("操作成功");
	}

	@PutMapping("/selected/all")
	@ApiOperation("全选/取消全选")
	public R<Void> selectAll(@RequestBody @Validated SelectCartItemDTO dto) {
		cartService.selectAll(UserContext.getUser(), dto.getSelected());
		return R.success("操作成功");
	}

	@GetMapping("/selected")
	@ApiOperation("获取已勾选的购物车商品（结算页用）")
	public R<List<CartItemVO>> getSelectedItems() {
		return R.success(cartService.getSelectedItems(UserContext.getUser()));
	}

	@DeleteMapping("/selected")
	@ApiOperation("移除已勾选的购物车商品（下单成功后调用）")
	public R<Void> removeSelected() {
		cartService.removeSelected(UserContext.getUser());
		return R.success("已移除");
	}
}
