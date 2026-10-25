package com.supermarket.pos.module.promotion.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车请求。收银台"算钱"和"下单"共用同一个结构，
 * 保证"屏幕上看到的金额"和"真正结算的金额"是同一套算法算出来的。
 */
@Data
public class CartRequest {

    private List<Item> items = new ArrayList<>();

    /** 会员 id，非会员传 null */
    private Long memberId;

    /** 选中的会员券（mem_member_coupon.id），0 或空表示不用券 */
    private List<Long> memberCouponIds = new ArrayList<>();

    /** 想使用的积分，0 表示不用 */
    private int usePoints;

    /** 挂单号，从挂单恢复时带上来，用于把挂单标记为已取 */
    private String holdNo;

    @Data
    public static class Item {
        private Long skuId;
        private Integer qty = 1;
        /** 称重商品的重量（kg），计件商品忽略 */
        private BigDecimal weight;
    }
}
