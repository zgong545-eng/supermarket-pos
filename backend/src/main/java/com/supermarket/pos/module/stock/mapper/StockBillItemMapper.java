package com.supermarket.pos.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supermarket.pos.module.stock.domain.StockBillItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockBillItemMapper extends BaseMapper<StockBillItem> {
}
