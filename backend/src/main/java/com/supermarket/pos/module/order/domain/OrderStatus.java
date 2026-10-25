package com.supermarket.pos.module.order.domain;

/**
 * 订单状态机（表驱动）。
 *
 * <p>为什么不写成一堆 if：订单状态的合法转移只有下面这几条，
 * 用一张表列出来，任何越界转移都会在 {@link #canGoTo} 里被拦下。
 * 答辩时可以直接对着这张表讲"哪 9 条合法、其余 26 种组合全部非法"。
 *
 * <pre>
 *   CREATED ──▶ PENDING_PAY ──▶ PAID ──▶ COMPLETED
 *      │             │           │           │
 *      │             │           └───────────┴──▶ REFUNDED
 *      ├──▶ HOLDING ─┤
 *      │             │
 *      └─────────────┴──────▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {

    /** 已建单，但还没锁定库存（短暂中间态，正常不会出现在界面上） */
    CREATED,
    /** 挂单中，货已预占 */
    HOLDING,
    /** 待支付，货已预占 */
    PENDING_PAY,
    /** 已支付，货已实扣 */
    PAID,
    /** 已完成（小票已出） */
    COMPLETED,
    /** 已取消，预占已释放 */
    CANCELLED,
    /** 已退货，货款已退、库存已回 */
    REFUNDED;

    /**
     * 当前状态能不能转到目标状态。
     *
     * <p>特别注意两条容易被写错的规则：
     * <ul>
     *   <li>PAID 不能回 CANCELLED —— 钱都收了，只能走退款（REFUNDED）</li>
     *   <li>CANCELLED / REFUNDED 是终态，不能再变 —— 否则对账会出现"已取消又完成了"</li>
     * </ul>
     */
    public boolean canGoTo(OrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == PENDING_PAY || target == HOLDING || target == CANCELLED;
            case HOLDING -> target == PENDING_PAY || target == CANCELLED;
            case PENDING_PAY -> target == PAID || target == CANCELLED;
            case PAID -> target == COMPLETED || target == REFUNDED;
            case COMPLETED -> target == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    /** 是否已占用库存（取消时要释放，退款时要回库） */
    public boolean holdsStock() {
        return this == HOLDING || this == PENDING_PAY || this == PAID || this == COMPLETED;
    }

    public static OrderStatus of(String name) {
        try {
            return valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
