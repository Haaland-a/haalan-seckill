/**
 * @description TUserMapper
 * @authorHaaland
 * @date 2026/4/13
 */
package com.haalan.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haalan.user.domain.po.TUser;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface TUserMapper extends BaseMapper<TUser> {
	/**
	 * 注册
	 *
	 * @param user
	 */


	void register(TUser user);
}
