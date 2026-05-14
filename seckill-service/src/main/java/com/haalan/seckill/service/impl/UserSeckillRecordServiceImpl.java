package com.haalan.seckill.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.domain.PageResult;
import com.haalan.seckill.domain.po.UserSeckillRecord;
import com.haalan.seckill.domain.vo.UserSeckillRecordVO;
import com.haalan.seckill.mapper.UserSeckillRecordMapper;
import com.haalan.seckill.service.IUserSeckillRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户秒杀记录表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSeckillRecordServiceImpl extends ServiceImpl<UserSeckillRecordMapper, UserSeckillRecord> implements IUserSeckillRecordService {

	private final UserSeckillRecordMapper userSeckillRecordMapper;

	@Override
	public PageResult<UserSeckillRecordVO> getUserRecords(Long userId, Integer pageNum, Integer pageSize) {
		// 1. 根据userId确定分表
		int tableSuffix = (int) (userId % 2);
		String tableName = "user_seckill_record_" + tableSuffix;
		log.debug("查询用户秒杀记录，userId: {}, 表名: {}", userId, tableName);

		// 2. 创建分页对象
		Page<UserSeckillRecord> page = new Page<>(pageNum, pageSize);

		// 3. 查询数据库（自定义SQL，需手动处理逻辑删除）不可以使用mybatis-plus的查询  因为实体类没有写表名也不能写
		Page<UserSeckillRecord> recordPage = userSeckillRecordMapper.selectPageByUserId(page, userId, tableName);

		// 4. 转换为VO对象
		List<UserSeckillRecordVO> voList = recordPage.getRecords().stream()
				.map(this::convertToVO)
				.collect(Collectors.toList());

		// 5. 构建分页结果
		PageResult<UserSeckillRecordVO> result = new PageResult<>();
		result.setTotal(recordPage.getTotal());
		result.setPageNum(pageNum);
		result.setPageSize(pageSize);
		result.setList(voList);

		return result;
	}

	/**
	 * 将PO转换为VO
	 */
	private UserSeckillRecordVO convertToVO(UserSeckillRecord record) {
		UserSeckillRecordVO vo = new UserSeckillRecordVO();
		BeanUtils.copyProperties(record, vo);

		// 设置状态名称
		vo.setStatusName(getStatusName(record.getStatus()));

		return vo;
	}

	/**
	 * 获取状态名称
	 */
	private String getStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		switch (status) {
			case 0:
				return "待支付";
			case 1:
				return "已支付";
			case 2:
				return "已取消";
			case 3:
				return "已完成";
			default:
				return "未知";
		}
	}

	@Override
	public void saveUserRecord(UserSeckillRecord record) {
		// 根据userId确定分表
		int tableSuffix = (int) (record.getUserId() % 2);
		String tableName = "user_seckill_record_" + tableSuffix;
		log.debug("保存用户秒杀记录，userId: {}, 表名: {}", record.getUserId(), tableName);

		// 插入数据库
		userSeckillRecordMapper.insertUserRecord(record, tableName);
	}
}
