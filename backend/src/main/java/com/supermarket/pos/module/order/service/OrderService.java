package com.supermarket.pos.module.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.module.member.service.MemberService;
import com.supermarket.pos.module.order.domain.OrderStatus;
import com.supermarket.pos.module.order.domain.SoOrder;
import com.supermarket.pos.module.order.mapper.SoOrderMapper;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.engine.PromotionEngine;
import com.supermarket.pos.module.promotion.service.PromotionService;
import com.supermarket.pos.module.shift.service.ShiftService;
import com.supermarket.pos.module.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M06 订单服务。
 *
 * <p>下单这条链路涉及四个模块，顺序不能乱：
 * <pre>算钱(促销) → 建单 → 预占库存 → 进入待支付</pre>
 * 任何一步失败，整个事务回滚，库存预占也会跟着回滚，不会出现"订单没了但货被锁住"。
 *
 * <p>另一个关键点：<b>库存在下单时就预占，不是等付款才扣。</b>
 * 否则两个收银台同时卖最后一件商品，都会算钱成功，最后一个付款失败，
 * 顾客在柜台前站着等，收银员也不知道怎么解释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SoOrderMapper orderMapper;
    private final PromotionService promotionService;
    private final StockService stockService;
    private final MemberService memberService;
    private final ShiftService shiftService;
    private final SequenceService sequenceService;
    private final JdbcTemplate jdbc;

    // ============================================================
    //  下单
    // ============================================================

    /**
     * 算钱 + 建单 + 预占库存。
     *
     * @return 已进入 PENDING_PAY 的订单
     */
    @Transactional
    public SoOrder create(CartRequest req, String channel) {
        PromotionEngine.Result calc = promotionService.calculate(req, true);

        Long storeId = UserContext.currentStoreId() == null ? 1L : UserContext.currentStoreId();
        String orderNo = sequenceService.next("SO");

        // 订单必须挂班次，否则交班时统计不到这一单。
        // ensureOpen() 在没开班时会自动补一个备用金为 0 的班次，避免演示时数字对不上。
        Long shiftId = "POS".equalsIgnoreCase(channel == null ? "POS" : channel)
                ? shiftService.ensureOpen() : null;

        // ---------- 1. 订单主表（先 CREATED，预占成功后再转 PENDING_PAY）----------
        SoOrder order = new SoOrder();
        order.setOrderNo(orderNo);
        order.setStoreId(storeId);
        order.setMemberId(req.getMemberId());
        // H5 订单没有收银员，cashier_id 留空，报表按 channel 区分来源
        order.setCashierId("H5".equalsIgnoreCase(channel) ? null : UserContext.currentUserId());
        order.setShiftId(shiftId);
        order.setChannel(channel == null ? "POS" : channel);
        order.setStatus(OrderStatus.CREATED.name());
        order.setItemCount(calc.getLines().size());
        order.setTotalAmount(calc.getTotalAmount());
        order.setDiscountAmount(calc.getTotalDiscount());
        order.setPayableAmount(calc.getPayableAmount());
        order.setPaidAmount(0L);
        order.setChangeAmount(0L);
        order.setPointUsed(calc.getPointUsed());
        order.setPointDeductAmount(calc.getPointAmount());
        orderMapper.insert(order);
        logStatus(orderNo, null, OrderStatus.CREATED, "下单");

        // ---------- 2. 明细 ----------
        for (PromotionEngine.CartLine line : calc.getLines()) {
            Long skuId = line.getSkuId();
            String skuCode = line.getSkuCode();
            // 称重商品把"份数"还原成 kg 存下来，退货时按 kg 算
            BigDecimal weight = line.getWeight();
            int qty = line.getQty();

            jdbc.update("""
                    INSERT INTO so_order_item
                        (order_no, line_no, sku_id, sku_code, sku_name, barcode, unit,
                         standard_price, sale_price, qty, weight, amount, discount_amount, net_amount,
                         member_price_used, cost_price)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, orderNo, line.getLineNo(), skuId, skuCode, line.getSkuName(),
                    line.getBarcode(), line.getUnit(),
                    line.getStandardPrice(), line.getSalePrice(), qty, weight,
                    line.getAmount(), line.getDiscountAmount(), line.getNetAmount(),
                    line.isMemberPriceUsed() ? 1 : 0, line.getCostPrice());
        }

        // ---------- 3. 优惠明细（带分摊，退货时按这个算每件商品该退多少）----------
        int seq = 1;
        for (PromotionEngine.DiscountItem d : calc.getDiscounts()) {
            String allocJson;
            try {
                allocJson = JSON.writeValueAsString(d.getAllocations());
            } catch (Exception e) {
                allocJson = "[]";
            }
            jdbc.update("""
                    INSERT INTO so_order_discount
                        (order_no, seq, source_type, source_no, name, amount, alloc_json)
                    VALUES (?,?,?,?,?,?,?)
                    """, orderNo, seq++, d.getSourceType(), d.getSourceNo(), d.getName(),
                    d.getAmount(), allocJson);
        }

        // ---------- 4. 预占库存（失败会抛异常，整单回滚）----------
        for (PromotionEngine.CartLine line : calc.getLines()) {
            stockService.lock(line.getSkuId(), storeId, line.getQty(), orderNo);
        }

        // ---------- 5. 状态推进 ----------
        transition(orderNo, OrderStatus.CREATED, OrderStatus.PENDING_PAY, "库存已预占，等待付款");

        order.setStatus(OrderStatus.PENDING_PAY.name());
        return order;
    }

    // ============================================================
    //  取消 / 完成 / 退货
    // ============================================================

    /** 取消订单：把预占的库存放回去 */
    @Transactional
    public void cancel(String orderNo, String reason) {
        SoOrder order = mustGet(orderNo);
        OrderStatus from = OrderStatus.of(order.getStatus());
        if (from == null || !from.canGoTo(OrderStatus.CANCELLED)) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单当前是「" + statusText(order.getStatus()) + "」，不能取消");
        }

        if (from.holdsStock()) {
            releaseStock(orderNo);
        }

        jdbc.update("""
                UPDATE so_order SET status = ?, cancelled_at = NOW(), cancel_reason = ?, updated_at = NOW()
                 WHERE order_no = ? AND status = ?
                """, OrderStatus.CANCELLED.name(), reason, orderNo, order.getStatus());
        logStatus(orderNo, from, OrderStatus.CANCELLED, reason);
    }

    /** 收银完成（小票已出）。支付成功后由支付模块推进，这里供补打/手工确认用 */
    @Transactional
    public void complete(String orderNo) {
        SoOrder order = mustGet(orderNo);
        OrderStatus from = OrderStatus.of(order.getStatus());
        if (from == OrderStatus.COMPLETED) {
            return;
        }
        if (from == null || !from.canGoTo(OrderStatus.COMPLETED)) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单当前是「" + statusText(order.getStatus()) + "」，不能完成");
        }
        jdbc.update("""
                UPDATE so_order SET status = ?, finished_at = NOW(), updated_at = NOW()
                 WHERE order_no = ? AND status = ?
                """, OrderStatus.COMPLETED.name(), orderNo, order.getStatus());
        logStatus(orderNo, from, OrderStatus.COMPLETED, "收银完成");
    }

    /**
     * 整单退货。
     *
     * <p>四件事必须都做，少一件账就不平：
     * 退钱 → 库存回库 → 扣回赠送的积分 → 券退回。
     */
    @Transactional
    public void refund(String orderNo, Long operatorId, String reason) {
        SoOrder order = mustGet(orderNo);
        OrderStatus from = OrderStatus.of(order.getStatus());
        if (from == null || !from.canGoTo(OrderStatus.REFUNDED)) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单当前是「" + statusText(order.getStatus()) + "」，不能退货");
        }

        // 1. 退款（现金/微信等都走同一条记录链）
        jdbc.update("""
                INSERT INTO pay_refund (refund_no, pay_no, order_no, amount, reason, status, operator_id, finished_at)
                SELECT CONCAT('RF', DATE_FORMAT(NOW(),'%Y%m%d'), LPAD(p.id,5,'0')),
                       p.pay_no, p.order_no, p.amount, ?, 'SUCCESS', ?, NOW()
                  FROM pay_payment p
                 WHERE p.order_no = ? AND p.status = 'SUCCESS'
                 LIMIT 1
                """, reason, operatorId, orderNo);

        jdbc.update("""
                UPDATE pay_payment SET status = 'REFUNDED', updated_at = NOW()
                 WHERE order_no = ? AND status = 'SUCCESS'
                """, orderNo);

        // 2. 库存回库
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT sku_id, qty FROM so_order_item WHERE order_no = ?", orderNo);
        List<StockService.BillItemForm> back = new ArrayList<>();
        for (Map<String, Object> it : items) {
            StockService.BillItemForm f = new StockService.BillItemForm();
            f.setSkuId(((Number) it.get("sku_id")).longValue());
            f.setQty(((Number) it.get("qty")).intValue());
            back.add(f);
        }
        if (!back.isEmpty()) {
            stockService.replenish(back, "退货入库 " + orderNo);
        }

        // 3. 积分：扣回送的，退回用的
        if (order.getMemberId() != null) {
            int earned = (int) (order.getPayableAmount() * 100 / 10000);
            if (earned > 0) {
                jdbc.update("UPDATE mem_member SET points = GREATEST(points - ?, 0) WHERE id = ?",
                        earned, order.getMemberId());
            }
            if (order.getPointUsed() != null && order.getPointUsed() > 0) {
                memberService.refundPoints(order.getMemberId(), order.getPointUsed(), orderNo);
            }
        }

        // 4. 券退回
        promotionService.restoreCoupons(orderNo);

        jdbc.update("""
                UPDATE so_order SET status = ?, updated_at = NOW() WHERE order_no = ?
                """, OrderStatus.REFUNDED.name(), orderNo);
        logStatus(orderNo, from, OrderStatus.REFUNDED, reason);
    }

    // ============================================================
    //  查询
    // ============================================================

    public SoOrder mustGet(String orderNo) {
        SoOrder order = orderMapper.selectOne(
                Wrappers.<SoOrder>lambdaQuery().eq(SoOrder::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public Map<String, Object> detail(String orderNo) {
        SoOrder order = mustGet(orderNo);
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", jdbc.queryForList("""
                SELECT line_no AS lineNo, sku_id AS skuId, sku_code AS skuCode, sku_name AS skuName,
                       barcode, unit, standard_price AS standardPrice, sale_price AS salePrice,
                       qty, weight, amount, discount_amount AS discountAmount, net_amount AS netAmount,
                       member_price_used AS memberPriceUsed
                  FROM so_order_item WHERE order_no = ? ORDER BY line_no
                """, orderNo));
        result.put("discounts", jdbc.queryForList("""
                SELECT seq, source_type AS sourceType, source_no AS sourceNo, name, amount, alloc_json AS allocJson
                  FROM so_order_discount WHERE order_no = ? ORDER BY seq
                """, orderNo));
        result.put("payments", jdbc.queryForList("""
                SELECT pay_no AS payNo, channel_code AS channelCode, amount, status,
                       channel_trade_no AS channelTradeNo, callback_count AS callbackCount, paid_at AS paidAt
                  FROM pay_payment WHERE order_no = ? ORDER BY id
                """, orderNo));
        result.put("statusLogs", jdbc.queryForList("""
                SELECT from_status AS fromStatus, to_status AS toStatus, remark, created_at AS createdAt
                  FROM so_order_status_log WHERE order_no = ? ORDER BY id
                """, orderNo));
        return result;
    }

    public List<Map<String, Object>> page(String keyword, String status, int page, int size) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbc.queryForList("""
                SELECT o.order_no AS orderNo, o.status, o.channel, o.item_count AS itemCount,
                       o.total_amount AS totalAmount, o.discount_amount AS discountAmount,
                       o.payable_amount AS payableAmount, o.paid_amount AS paidAmount,
                       o.created_at AS createdAt, o.paid_at AS paidAt,
                       m.name AS memberName, m.phone AS memberPhone,
                       u.real_name AS cashierName
                  FROM so_order o
                  LEFT JOIN mem_member m ON m.id = o.member_id
                  LEFT JOIN sys_user u ON u.id = o.cashier_id
                 WHERE (? = '' OR o.status = ?) AND o.order_no LIKE ?
                 ORDER BY o.id DESC LIMIT ? OFFSET ?
                """, status == null ? "" : status, status == null ? "" : status, like,
                size, (page - 1) * size);
    }

    public Map<String, Object> todaySummary() {
        Map<String, Object> r = new HashMap<>();
        r.put("orderCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM so_order WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','COMPLETED')",
                Integer.class));
        r.put("amount", jdbc.queryForObject(
                "SELECT COALESCE(SUM(payable_amount),0) FROM so_order WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','COMPLETED')",
                Long.class));
        return r;
    }

    // ============================================================
    //  内部：状态流转与库存回滚
    // ============================================================

    public void transition(String orderNo, OrderStatus from, OrderStatus to, String remark) {
        if (from != null && !from.canGoTo(to)) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不能从「" + statusText(from.name()) + "」跳到「" + statusText(to.name()) + "」");
        }
        jdbc.update("UPDATE so_order SET status = ?, updated_at = NOW() WHERE order_no = ?",
                to.name(), orderNo);
        logStatus(orderNo, from, to, remark);
    }

    private void releaseStock(String orderNo) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT sku_id, qty FROM so_order_item WHERE order_no = ?", orderNo);
        Long storeId = mustGet(orderNo).getStoreId();
        for (Map<String, Object> it : items) {
            stockService.release(((Number) it.get("sku_id")).longValue(), storeId,
                    ((Number) it.get("qty")).intValue(), orderNo);
        }
    }

    private void logStatus(String orderNo, OrderStatus from, OrderStatus to, String remark) {
        jdbc.update("""
                INSERT INTO so_order_status_log (order_no, from_status, to_status, operator_id, remark)
                VALUES (?,?,?,?,?)
                """, orderNo, from == null ? null : from.name(), to.name(),
                UserContext.currentUserId(), remark);
    }

    public static String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "CREATED" -> "已建单";
            case "HOLDING" -> "挂单中";
            case "PENDING_PAY" -> "待支付";
            case "PAID" -> "已支付";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "REFUNDED" -> "已退货";
            default -> status;
        };
    }

    /**
     * 给支付模块用：支付成功后推进订单状态。
     *
     * <p><b>这里必须用 jdbc 直接读状态，不能用 orderMapper。</b>
     * 原因：整个结算是同一个事务，MyBatis 的一级缓存会一直挂着；
     * 而本类的写操作走的是 JdbcTemplate，MyBatis 并不知道数据变了，
     * 于是第二次读到的还是"待支付"。表现出来就是订单卡在 PAID 不动。
     * 这类"ORM 缓存 + 原生 SQL 混用"的坑只在事务内出现，非常难查。
     */
    @Transactional
    public void markPaid(String orderNo, long paidAmount, String channelCode) {
        Map<String, Object> row = rowOf(orderNo);
        OrderStatus from = OrderStatus.of((String) row.get("status"));
        if (from == OrderStatus.PAID || from == OrderStatus.COMPLETED) {
            return;     // 幂等：重复回调不报错，也不重复改
        }
        if (from == null || !from.canGoTo(OrderStatus.PAID)) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单当前是「" + statusText((String) row.get("status")) + "」，不能支付");
        }

        long payable = ((Number) row.get("payable_amount")).longValue();
        long change = Math.max(0, paidAmount - payable);
        jdbc.update("""
                UPDATE so_order SET status = ?, paid_amount = ?, change_amount = ?,
                       paid_at = NOW(), updated_at = NOW()
                 WHERE order_no = ? AND status = ?
                """, OrderStatus.PAID.name(), paidAmount, change, orderNo, row.get("status"));
        logStatus(orderNo, from, OrderStatus.PAID, "支付成功（" + channelCode + "）");
    }

    /** 支付成功后完成订单（出小票）。同样走 jdbc 读，理由见 markPaid。 */
    @Transactional
    public void finishAfterPay(String orderNo) {
        String current = jdbc.queryForObject(
                "SELECT status FROM so_order WHERE order_no = ?", String.class, orderNo);
        OrderStatus from = OrderStatus.of(current);
        if (from == OrderStatus.COMPLETED) {
            return;
        }
        if (from == OrderStatus.PAID) {
            jdbc.update("""
                    UPDATE so_order SET status = ?, finished_at = NOW(), updated_at = NOW()
                     WHERE order_no = ? AND status = 'PAID'
                    """, OrderStatus.COMPLETED.name(), orderNo);
            logStatus(orderNo, from, OrderStatus.COMPLETED, "交易完成");
        }
    }

    /** 直接用 jdbc 读整行，绕开 ORM 缓存 */
    private Map<String, Object> rowOf(String orderNo) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM so_order WHERE order_no = ?", orderNo);
        if (rows.isEmpty()) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        return rows.get(0);
    }

    /** 交易日期，报表按天聚合用 */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
