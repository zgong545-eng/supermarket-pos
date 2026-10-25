package com.supermarket.pos.module.shift.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M08 收银交班。
 *
 * <p>为什么要交班？因为收银机里有现金。一天下来抽屉里的钱对不对得上，
 * 必须有个"我交出去的时候是这么多"的书面记录，否则少了钱说不清是谁的班。
 *
 * <p>长短款 = 实点现金 - (备用金 + 本班现金收入)。
 * 正数叫"长款"（多出来了，多半是少找了钱），负数叫"短款"（少了，多半是多找了或拿走了）。
 * 这个数不参与账务，只是管理线索——课堂上老师常问"短款怎么办"，
 * 标准答案是"记录、复盘、不冲销账目"，因为账目必须跟银行流水一致。
 */
@Data
@TableName("pos_shift")
public class PosShift {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shiftNo;
    private Long storeId;
    private Long cashierId;
    private String cashierName;
    private String deviceCode;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    /** OPEN 当班中 / CLOSED 已交班 */
    private String status;

    /** 备用金（开班时抽屉里原有的钱，用于找零） */
    private Long openingCash;

    /** 实点现金（交班时数出来的钱） */
    private Long closingCash;

    /** 系统应收现金 = 备用金 + 本班现金收入 */
    private Long systemCash;

    /** 长短款 = 实点 - 系统 */
    private Long cashDiff;

    private Integer orderCount;
    private Long totalAmount;
    private String remark;
}
