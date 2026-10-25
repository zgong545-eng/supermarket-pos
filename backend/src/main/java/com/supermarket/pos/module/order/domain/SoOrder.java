package com.supermarket.pos.module.order.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单主表。金额全部是「分」。 */
@Data
@TableName("so_order")
public class SoOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long storeId;
    private Long memberId;
    private Long cashierId;
    private Long shiftId;

    /** POS 收银台 / H5 会员端 */
    private String channel;

    /** 见 {@link OrderStatus} */
    private String status;

    private Integer itemCount;

    /** 原价合计 */
    private Long totalAmount;
    /** 优惠合计 */
    private Long discountAmount;
    /** 应收 = 原价 - 优惠 */
    private Long payableAmount;
    /** 实收 */
    private Long paidAmount;
    /** 找零 */
    private Long changeAmount;

    private Integer pointUsed;
    private Long pointDeductAmount;

    private String remark;

    @Version
    private Integer version;

    private LocalDateTime paidAt;
    private LocalDateTime finishedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
