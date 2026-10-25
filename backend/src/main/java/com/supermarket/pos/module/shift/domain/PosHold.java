package com.supermarket.pos.module.shift.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M08 挂单。
 *
 * <p>场景：顾客扫到一半说"我再去拿个东西"，后面排了五个人。
 * 收银员把这车货挂起，先服务下一个，顾客回来再取出来接着扫。
 *
 * <p>挂单只存购物车快照（JSON），<b>不占库存</b>。
 * 如果挂单也占库存，恶意挂单就能把货锁死——这是电商系统里被刷过的经典漏洞，
 * 所以挂单必须设置有效期（本系统由前端提示"超过 2 小时自动作废"）。
 */
@Data
@TableName("pos_hold")
public class PosHold {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String holdNo;
    private Long storeId;
    private Long cashierId;

    /** 购物车快照：既存输入（skuId + qty），也存展示用的名字和金额，取单时不用再查一遍 */
    private String itemsJson;

    private Long totalAmount;

    /** HELD 挂起中 / RESUMED 已取出 / CANCELLED 已作废 */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime resumedAt;
}
