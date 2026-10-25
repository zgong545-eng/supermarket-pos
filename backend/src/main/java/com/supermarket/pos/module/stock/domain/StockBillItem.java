package com.supermarket.pos.module.stock.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 库存单据明细 */
@Data
@TableName("stock_bill_item")
public class StockBillItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long billId;
    private String billNo;
    private Long skuId;
    private Integer qty;
    private Long costPrice;
    private Long amount;
    private String remark;
}
