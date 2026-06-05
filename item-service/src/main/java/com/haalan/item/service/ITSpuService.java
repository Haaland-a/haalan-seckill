package com.haalan.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageDTO;
import com.haalan.item.domain.dto.SpuCreateDTO;
import com.haalan.item.domain.dto.SpuQueryDTO;
import com.haalan.item.domain.dto.SpuUpdateDTO;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.SpuCreateResultVO;
import com.haalan.item.domain.vo.SpuListVO;


public interface ITSpuService extends IService<TSpu> {

	SpuCreateResultVO createSpu(SpuCreateDTO dto);

	PageDTO<SpuListVO> querySpuList(SpuQueryDTO queryDTO);

	void updateSpuStatus(Long spuId, Integer status);

	void updateSpu(Long spuId, SpuUpdateDTO dto);

}
