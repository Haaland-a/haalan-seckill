package com.haalan.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.mapper.TBrandMapper;
import com.haalan.item.service.ITBrandService;
import org.springframework.stereotype.Service;

@Service
public class TBrandServiceImpl extends ServiceImpl<TBrandMapper, TBrand> implements ITBrandService {

}