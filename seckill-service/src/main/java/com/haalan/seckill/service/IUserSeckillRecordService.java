package com.haalan.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageResult;
import com.haalan.seckill.domain.po.UserSeckillRecord;
import com.haalan.seckill.domain.vo.UserSeckillRecordVO;

/**
 * <p>
 * 用户秒杀记录表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-13
 */
public interface IUserSeckillRecordService extends IService<UserSeckillRecord> {

	/**
	 * 分页查询用户秒杀记录
	 *
	 * @param userId   用户ID
	 * @param pageNum  页码
	 * @param pageSize 每页大小
	 * @return 分页结果
	 */
	PageResult<UserSeckillRecordVO> getUserRecords(Long userId, Integer pageNum, Integer pageSize);

	/**
	 * 保存用户秒杀记录（支持分表）
	 *
	 * @param record 秒杀记录
	 */
	void saveUserRecord(UserSeckillRecord record);

	/**
	 * 根据订单号更新秒杀记录状态（支持分表）
	 *
	 * @param orderNo 订单号
	 * @param userId  用户ID（用于确定分表）
	 * @param status  新状态
	 * @return 是否更新成功
	 */
	boolean updateStatusByOrderNo(String orderNo, Long userId, Integer status);

	UserSeckillRecord getByOrderNo(String orderNo, Long userId);
}
