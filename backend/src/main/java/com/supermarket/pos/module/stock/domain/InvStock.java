package com.supermarket.pos.module.stock.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存。
 *
 * <p><b>核心不变式：{@code on_hand = available + locked}。</b>
 * 任何时候这条都要成立，跑起来后可以用 SQL 随时验证：
 * <pre>
 * SELECT COUNT(*) FROM inv_stock WHERE on_hand &lt;&gt; available + locked;  -- 必须是 0
 * </pre>
 *
 * <p>三个量的含义：
 * <ul>
 *   <li>on_hand —— 账面上有多少货（含仓库里没上架的）</li>
 *   <li>locked  —— 已经被下单但还没付钱的（顾客正在扫码，货不能卖给别人）</li>
 *   <li>available —— 现在还能卖多少</li>
 * </ul>
 */
@Data
@TableName("inv_stock")
public class InvStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;
    private Long storeId;

    private Integer onHand;
    private Integer available;
    private Integer locked;
    private Integer safetyStock;

    /** 乐观锁版本号，给"读出来改再写回"的场景兜底 */
    @Version
    private Integer version;

    private LocalDateTime updatedAt;

    public boolean isBelowSafety() {
        return available != null && safetyStock != null && available < safetyStock;
    }
}
