package com.supermarket.pos.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supermarket.pos.module.stock.domain.StockBill;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockBillMapper extends BaseMapper<StockBill> {
}
