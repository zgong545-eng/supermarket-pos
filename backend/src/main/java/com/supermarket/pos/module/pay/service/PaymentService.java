package com.supermarket.pos.module.pay.service;

import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.module.member.service.MemberService;
import com.supermarket.pos.module.order.domain.OrderStatus;
import com.supermarket.pos.module.order.domain.SoOrder;
import com.supermarket.pos.module.order.mapper.SoOrderMapper;
import com.supermarket.pos.module.order.service.OrderService;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.service.PromotionService;
import com.supermarket.pos.module.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * M07 支付服务。
 *
 * <p><b>这个类只有一个真正的难点：幂等。</b>
 *
 * <p>真实世界里支付回调一定会重复：网络超时重发、微信/支付宝的补偿机制、
 * 用户连点两下"确认支付"。如果回调代码写成了"收到回调就加钱/改状态"，
 * 那么重复回调就会重复发货、重复加积分。
 *
 * <p>这里用三道闸：
 * <ol>
 *   <li><b>状态条件更新</b>：{@code UPDATE ... WHERE status='INIT'}，
 *       只有一个线程能把 INIT 改成 SUCCESS，其余全部影响 0 行</li>
 *   <li><b>channel_trade_no 唯一索引</b>：就算代码写漏了，数据库也不让插两条</li>
 *   <li><b>订单状态机</b>：PAID 之后不允许再 PAID，重复推进直接被状态机挡掉</li>
 * </ol>
 *
 * <p>课堂演示方法：在 POS 的演示面板上连点两次"支付成功回调"，
 * 第二次会返回「该笔支付已处理过」，而订单金额、库存、积分都不会变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final JdbcTemplate jdbc;
    private final SequenceService sequenceService;
    private final OrderService orderService;
    private final SoOrderMapper orderMapper;
    private final StockService stockService;
    private final MemberService memberService;
    private final PromotionService promotionService;

    // ============================================================
    //  发起支付
    // ============================================================

    /**
     * 创建支付单（状态 INIT）。
     * 真正的扣款发生在 {@link #handleCallback} 里。
     */
    @Transactional
    public Map<String, Object> create(String orderNo, String channelCode) {
        SoOrder order = orderService.mustGet(orderNo);
        if (OrderStatus.of(order.getStatus()) == OrderStatus.PAID
                || OrderStatus.of(order.getStatus()) == OrderStatus.COMPLETED) {
            throw BizException.of(ErrorCode.ORDER_ALREADY_PAID);
        }
        Integer enabled = jdbc.queryForObject(
                "SELECT enabled FROM pay_channel WHERE code = ?", Integer.class, channelCode);
        if (enabled == null || enabled != 1) {
            throw BizException.of(ErrorCode.PAY_CHANNEL_DISABLED);
        }

        String payNo = sequenceService.next("PY");
        jdbc.update("""
                INSERT INTO pay_payment (pay_no, order_no, channel_code, amount, status)
                VALUES (?,?,?,?, 'INIT')
                """, payNo, orderNo, channelCode, order.getPayableAmount());

        return Map.of(
                "payNo", payNo,
                "orderNo", orderNo,
                "channelCode", channelCode,
                "amount", order.getPayableAmount(),
                "status", "INIT"
        );
    }

    /**
     * 现金收款：一步到位。
     *
     * <p>现金没有"回调"，收银员点"收款"就是钱到手了。
     * 但仍然要生成支付单，否则报表按支付方式统计时现金那一栏会是空的。
     */
    @Transactional
    public Map<String, Object> cash(String orderNo, Long tendered) {
        SoOrder order = orderService.mustGet(orderNo);
        long payable = order.getPayableAmount();
        long received = tendered == null || tendered <= 0 ? payable : tendered;
        if (received < payable) {
            throw BizException.of(ErrorCode.ORDER_AMOUNT_MISMATCH,
                    "实收不足，应收 " + yuan(payable) + " 元");
        }

        Map<String, Object> created = create(orderNo, "CASH");
        String payNo = (String) created.get("payNo");

        // 现金没有回调，但支付单同样要走"INIT → SUCCESS"这一步。
        // 少了这一步会有两个后果：报表按渠道统计时现金栏永远是空的；
        // 交班的"系统应收现金"也算不出来（它按 p.status='SUCCESS' AND CASH 求和）。
        String tradeNo = "CASH" + System.currentTimeMillis();
        if (claimSuccess(payNo, tradeNo) == 0) {
            throw BizException.of(ErrorCode.PAY_DUPLICATED);
        }
        settle(payNo, tradeNo, received);
        return Map.of(
                "payNo", payNo,
                "payable", payable,
                "received", received,
                "change", received - payable,
                "orderNo", orderNo
        );
    }

    /**
     * 把支付单从 INIT 改判为 SUCCESS。
     *
     * <p>这是幂等的第一道闸：带 {@code AND status = 'INIT'} 条件，
     * 并发或重复调用时只有第一次能改成功，返回 0 就说明已经被处理过。
     * 现金收款和扫码回调都走这一个方法，保证两条路径的判定完全一致。
     *
     * @return 影响行数，1 = 本次抢到了处理权，0 = 已被别人处理
     */
    private int claimSuccess(String payNo, String tradeNo) {
        return jdbc.update("""
                UPDATE pay_payment
                   SET status = 'SUCCESS', channel_trade_no = ?, paid_at = NOW(),
                       callback_count = callback_count + 1, updated_at = NOW()
                 WHERE pay_no = ? AND status = 'INIT'
                """, tradeNo, payNo);
    }

    // ============================================================
    //  回调（幂等的核心）
    // ============================================================

    /**
     * 模拟支付回调。
     *
     * <p>课堂演示版不需要真的接微信：POS 面板上点一下按钮，
     * 内部调用这个方法，但走进来的逻辑跟真实回调<b>完全一样</b>——
     * 同样查支付单、同样做幂等、同样扣库存、同样加积分。
     * 这样演示的是真实逻辑，不是一段假代码。
     *
     * @param success       true 表示支付成功
     * @param channelTradeNo 渠道流水号；不传则自动生成。重复传同一个值可以演示"幂等"
     */
    @Transactional
    public Map<String, Object> handleCallback(String payNo, boolean success, String channelTradeNo, long delayMs) {
        if (delayMs > 0) {
            try {
                Thread.sleep(Math.min(delayMs, 5000));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM pay_payment WHERE pay_no = ?", payNo);
        if (rows.isEmpty()) {
            throw BizException.of(ErrorCode.PAY_NOT_FOUND);
        }
        Map<String, Object> pay = rows.get(0);
        String status = (String) pay.get("status");
        String orderNo = (String) pay.get("order_no");
        Long amount = ((Number) pay.get("amount")).longValue();
        String tradeNo = channelTradeNo == null || channelTradeNo.isBlank()
                ? "MOCK" + System.currentTimeMillis()
                : channelTradeNo;

        // ---------- 闸门 1：条件更新，只有一个线程能改 ----------
        int claimed;
        if (success) {
            claimed = claimSuccess(payNo, tradeNo);
        } else {
            claimed = jdbc.update("""
                    UPDATE pay_payment
                       SET status = 'FAILED', callback_count = callback_count + 1, updated_at = NOW()
                     WHERE pay_no = ? AND status = 'INIT'
                    """, payNo);
        }

        if (claimed == 0) {
            // 已经处理过了：把回调次数 +1 作为证据，但绝不再改金额和状态
            jdbc.update("UPDATE pay_payment SET callback_count = callback_count + 1 WHERE pay_no = ?", payNo);
            int count = jdbc.queryForObject(
                    "SELECT callback_count FROM pay_payment WHERE pay_no = ?", Integer.class, payNo);
            log.warn("重复回调被忽略：payNo={} 当前状态={} 已收到 {} 次回调", payNo, status, count);
            return Map.of(
                    "accepted", false,
                    "reason", "该笔支付已处理过，本次回调已忽略",
                    "status", status,
                    "callbackCount", count,
                    "orderNo", orderNo
            );
        }

        if (!success) {
            return Map.of("accepted", true, "status", "FAILED", "orderNo", orderNo, "callbackCount", 1);
        }

        // ---------- 真正结算 ----------
        settle(payNo, tradeNo, amount);

        int count = jdbc.queryForObject(
                "SELECT callback_count FROM pay_payment WHERE pay_no = ?", Integer.class, payNo);
        return Map.of(
                "accepted", true,
                "status", "SUCCESS",
                "orderNo", orderNo,
                "amount", amount,
                "callbackCount", count
        );
    }

    /**
     * 结算：支付成功后要做的所有事，全在一个事务里。
     *
     * <p>顺序有讲究：先扣储值（可能失败，要回滚）→ 扣库存 → 加积分 → 核销券 → 改订单状态。
     * 如果先改了订单状态再扣储值，储值余额不足时订单已经"已支付"，退都退不回来。
     */
    private void settle(String payNo, String tradeNo, long amount) {
        Map<String, Object> pay = jdbc.queryForMap("SELECT * FROM pay_payment WHERE pay_no = ?", payNo);
        String orderNo = (String) pay.get("order_no");
        String channel = (String) pay.get("channel_code");

        SoOrder order = orderService.mustGet(orderNo);
        Long storeId = order.getStoreId();

        // 1. 储值支付：余额不足会抛异常，整个结算回滚，支付单仍是 INIT，可以重试
        if ("WALLET".equals(channel)) {
            if (order.getMemberId() == null) {
                throw BizException.of(ErrorCode.MEMBER_NOT_FOUND, "储值支付必须是会员订单");
            }
            memberService.walletPay(order.getMemberId(), amount, orderNo);
        }

        // 2. 库存实扣（预占 → 出库）
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT sku_id, qty FROM so_order_item WHERE order_no = ?", orderNo);
        for (Map<String, Object> it : items) {
            stockService.commit(((Number) it.get("sku_id")).longValue(), storeId,
                    ((Number) it.get("qty")).intValue(), orderNo);
        }

        // 3. 会员积分：按等级倍率送，同时扣掉本单使用的积分
        if (order.getMemberId() != null) {
            if (order.getPointUsed() != null && order.getPointUsed() > 0) {
                memberService.deductPoints(order.getMemberId(), order.getPointUsed(), orderNo);
            }
            int rate = jdbc.queryForObject("""
                    SELECT l.point_rate FROM mem_member m JOIN mem_level l ON l.id = m.level_id
                     WHERE m.id = ?
                    """, Integer.class, order.getMemberId());
            int points = (int) (order.getPayableAmount() * rate / 10000);
            if (points > 0) {
                memberService.earnPoints(order.getMemberId(), points, orderNo);
            }
        }

        // 4. 券核销
        List<Long> usedCoupons = jdbc.queryForList("""
                SELECT mc.id FROM mem_member_coupon mc
                 WHERE mc.order_no IS NULL AND mc.member_id = ? AND mc.status = 'UNUSED'
                   AND EXISTS (SELECT 1 FROM so_order_discount d
                                WHERE d.order_no = ? AND d.source_type = 'COUPON')
                 LIMIT 5
                """, Long.class, order.getMemberId(), orderNo);
        if (!usedCoupons.isEmpty()) {
            promotionService.markCouponsUsed(usedCoupons, order.getMemberId(), orderNo);
        }

        // 5. 订单状态：PAID → COMPLETED
        orderService.markPaid(orderNo, amount, channel);
        orderService.finishAfterPay(orderNo);

        // 6. 小票记录
        jdbc.update("""
                INSERT IGNORE INTO pos_receipt (order_no, receipt_no, print_count)
                VALUES (?, CONCAT('RC', DATE_FORMAT(NOW(),'%Y%m%d%H%i%s'), LPAD(FLOOR(RAND()*1000),3,'0')), 1)
                """, orderNo);

        // 7. 交班统计累加
        if (order.getShiftId() != null) {
            jdbc.update("""
                    UPDATE pos_shift SET order_count = order_count + 1,
                           total_amount = total_amount + ?
                     WHERE id = ?
                    """, order.getPayableAmount(), order.getShiftId());
        }

        log.info("支付结算完成：orderNo={} channel={} amount={} tradeNo={}", orderNo, channel, amount, tradeNo);
    }

    // ============================================================
    //  查询
    // ============================================================

    public List<Map<String, Object>> byOrder(String orderNo) {
        return jdbc.queryForList("""
                SELECT pay_no AS payNo, channel_code AS channelCode, amount, status,
                       channel_trade_no AS channelTradeNo, callback_count AS callbackCount,
                       paid_at AS paidAt, created_at AS createdAt
                  FROM pay_payment WHERE order_no = ? ORDER BY id
                """, orderNo);
    }

    private String yuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
