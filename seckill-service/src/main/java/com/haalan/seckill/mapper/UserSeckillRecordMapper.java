package com.haalan.seckill.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haalan.seckill.domain.po.UserSeckillRecord;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 用户秒杀记录表 Mapper 接口
 * </p>
 *
 * @author haaland
 * @since 2026-05-13
 */
public interface UserSeckillRecordMapper extends BaseMapper<UserSeckillRecord> {

	/**
	 * 分页查询用户秒杀记录（支持分表）
	 *
	 * @param page      分页对象
	 * @param userId    用户ID
	 * @param tableName 表名（user_seckill_record_0 或 user_seckill_record_1）
	 * @return 分页结果
	 */
	Page<UserSeckillRecord> selectPageByUserId(@Param("page") Page<UserSeckillRecord> page,
											   @Param("userId") Long userId,
											   @Param("tableName") String tableName);

	/**
	 * 插入用户秒杀记录（支持分表）
	 *
	 * @param record    秒杀记录
	 * @param tableName 表名（user_seckill_record_0 或 user_seckill_record_1）
	 */
	void insertUserRecord(@Param("record") UserSeckillRecord record,
						  @Param("tableName") String tableName);
}
