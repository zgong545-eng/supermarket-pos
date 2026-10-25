package com.supermarket.pos.module.stock.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存单据：补货入库 / 报损 / 盘点。
 *
 * <p>课堂演示版刻意<b>不做审批流</b>：提交即生效（status 直接 CONFIRMED）。
 * 因为这套系统是单店自治，没有上级可以审批；加了审批反而演示时要多点两下。
 * 但单据本身必须留：谁在什么时候补了 100 瓶牛奶，这是可追溯的。
 */
@Data
@TableName("stock_bill")
public class StockBill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String billNo;

    /** INBOUND 补货入库 / LOSS 报损 / CHECK 盘点 */
    private String type;

    /** MANUAL 后台手工录入 / SYSTEM 系统生成 */
    private String source;

    /** DRAFT / CONFIRMED */
    private String status;

    private Integer totalQty;
    private Long totalAmount;
    private String remark;
    private Long operatorId;
    private LocalDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
