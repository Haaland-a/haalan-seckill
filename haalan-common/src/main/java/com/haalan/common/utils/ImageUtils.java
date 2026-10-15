package com.haalan.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import java.util.List;

/**
 * 商品图片字段解析工具。
 * <p>
 * 商品图片在库里存在三种历史形态，直接塞进 {@code <img src="">} 都会裂图：
 * <ol>
 *     <li>JSON 数组字符串：{@code ["https://a.jpg","https://b.jpg"]}</li>
 *     <li>逗号分隔：{@code https://a.jpg,https://b.jpg}</li>
 *     <li>单个 URL：{@code https://a.jpg}</li>
 * </ol>
 * 秒杀订单明细之前直接把 JSON 数组原样落进 product_image，
 * 用户端订单列表 el-image 拿到 {@code ["https://..."]} 当 URL 用 → 全部裂图。
 * 普通订单已经用 extractFirstImage 处理过，这里抽到公共模块让两边共用同一份实现。
 */
public class ImageUtils {

	private ImageUtils() {
	}

	/**
	 * 取出可直接用于 {@code <img src>} 的第一张图片 URL。
	 *
	 * @param images 图片字段原始值（任意形态）
	 * @return 第一张图片 URL；解析不出时返回 null
	 */
	public static String extractFirstImage(String images) {
		if (StrUtil.isBlank(images)) {
			return null;
		}
		String trimmed = images.trim();
		// JSON 数组形态
		if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
			try {
				List<String> list = JSONUtil.toList(trimmed, String.class);
				if (list != null && !list.isEmpty() && StrUtil.isNotBlank(list.get(0))) {
					return list.get(0);
				}
			} catch (Exception e) {
				// JSON 解析失败退回按逗号切分
			}
		}
		// 逗号分隔形态
		String[] arr = trimmed.split(",");
		for (String s : arr) {
			String url = s.trim().replace("[", "").replace("]", "").replace("\"", "");
			if (StrUtil.isNotBlank(url)) {
				return url;
			}
		}
		return null;
	}
}