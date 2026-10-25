package com.supermarket.pos.module.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M09 报表服务。
 *
 * <p><b>口径统一在这里，别的地方不许各算各的。</b>
 * "销售额"到底指原价、优惠后、还是实收？如果每个页面各写一个 SQL，
 * 迟早出现"订单页显示 1 万、报表页显示 9 千"，而且谁也说不清哪个对。
 *
 * <p>本系统统一口径：
 * <ul>
 *   <li>销售额 = {@code payable_amount} 之和（优惠后、退货前）</li>
 *   <li>只统计 {@code PAID / COMPLETED}，待支付和已取消不计</li>
 *   <li>退货单从销售额里扣掉（{@code REFUNDED} 单独一列展示，不混进净额）</li>
 * </ul>
 *
 * <p>实现上直接查 {@code so_order} 现算，不读预聚合表——数据量是几百条，
 * 现算比维护缓存更不容易错。{@code rebuild} 会把结果刷进 {@code rpt_*} 表，
 * 那个表留给"要大屏看板时直接读"的场景。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final JdbcTemplate jdbc;

    /** 首页总览：KPI + 趋势 + 支付方式 + 热销榜 */
    public Map<String, Object> overview(int days) {
        days = Math.max(1, Math.min(days, 90));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kpi", kpi(days));
        r.put("series", series(days));
        r.put("channels", channels(days));
        r.put("topSku", topSku(days, 10));
        r.put("hourly", hourly());
        return r;
    }

    /** 核心指标 */
    public Map<String, Object> kpi(int days) {
        Map<String, Object> today = oneDay(0);
        Map<String, Object> yesterday = oneDay(1);
        Map<String, Object> range = oneRange(days);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("today", today);
        r.put("yesterday", yesterday);
        r.put("range", range);
        r.put("days", days);

        long t = toLong(today.get("netAmount"));
        long y = toLong(yesterday.get("netAmount"));
        r.put("dayOverDay", y == 0 ? null : Math.round((t - y) * 1000.0 / y) / 10.0);
        return r;
    }

    /** 按天趋势，缺的天补 0（否则折线图会断） */
    public List<Map<String, Object>> series(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DATE(created_at) AS d, COUNT(*) AS orders,
                       COALESCE(SUM(payable_amount),0) AS amount,
                       COALESCE(SUM(discount_amount),0) AS discount
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND created_at >= ?
                 GROUP BY DATE(created_at)
                """, from);

        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(row.get("d")), row);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).toString();
            Map<String, Object> row = byDate.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key.substring(5));       // MM-dd，前端直接用
            item.put("orders", row == null ? 0 : row.get("orders"));
            item.put("amount", row == null ? 0 : row.get("amount"));
            item.put("discount", row == null ? 0 : row.get("discount"));
            out.add(item);
        }
        return out;
    }

    /** 支付方式占比 */
    public List<Map<String, Object>> channels(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        return jdbc.queryForList("""
                SELECT p.channel_code AS channel, COUNT(*) AS cnt,
                       COALESCE(SUM(p.amount),0) AS amount
                  FROM pay_payment p
                  JOIN so_order o ON o.order_no = p.order_no
                 WHERE p.status = 'SUCCESS' AND o.created_at >= ?
                 GROUP BY p.channel_code
                 ORDER BY amount DESC
                """, from);
    }

    /** 商品排行 */
    public List<Map<String, Object>> topSku(int days, int limit) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        return jdbc.queryForList("""
                SELECT i.sku_id AS skuId, i.sku_name AS skuName, SUM(i.qty) AS qty,
                       COALESCE(SUM(i.net_amount),0) AS amount
                  FROM so_order_item i
                  JOIN so_order o ON o.order_no = i.order_no
                 WHERE o.status IN ('PAID','COMPLETED') AND o.created_at >= ?
                 GROUP BY i.sku_id, i.sku_name
                 ORDER BY amount DESC
                 LIMIT ?
                """, from, limit);
    }

    /** 今日分时销售，用来看"哪个时段最忙" */
    public List<Map<String, Object>> hourly() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT HOUR(created_at) AS h, COUNT(*) AS orders,
                       COALESCE(SUM(payable_amount),0) AS amount
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND DATE(created_at) = CURDATE()
                 GROUP BY HOUR(created_at)
                """);
        Map<Integer, Map<String, Object>> byHour = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byHour.put(((Number) row.get("h")).intValue(), row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int h = 8; h <= 22; h++) {
            Map<String, Object> row = byHour.get(h);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", h + ":00");
            item.put("orders", row == null ? 0 : row.get("orders"));
            item.put("amount", row == null ? 0 : row.get("amount"));
            out.add(item);
        }
        return out;
    }

    /** 会员贡献对比 */
    public Map<String, Object> memberContribution(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> m = jdbc.queryForMap("""
                SELECT COUNT(*) AS orders, COALESCE(SUM(payable_amount),0) AS amount
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND created_at >= ? AND member_id IS NOT NULL
                """, from);
        Map<String, Object> n = jdbc.queryForMap("""
                SELECT COUNT(*) AS orders, COALESCE(SUM(payable_amount),0) AS amount
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND created_at >= ? AND member_id IS NULL
                """, from);
        r.put("member", m);
        r.put("nonMember", n);
        return r;
    }

    /**
     * 把某天的数据刷进预聚合表。
     * 适合放在每晚的定时任务里；演示时手动调一次即可。
     */
    @Transactional
    public Map<String, Object> rebuild(int days) {
        days = Math.max(1, Math.min(days, 90));
        LocalDate from = LocalDate.now().minusDays(days - 1);
        int rows = jdbc.update("""
                INSERT INTO rpt_daily_sales
                    (stat_date, store_id, order_count, item_count, member_order_count,
                     total_amount, discount_amount, net_amount, refund_amount,
                     cash_amount, wechat_amount, alipay_amount, wallet_amount, cost_amount, updated_at)
                SELECT DATE(o.created_at), 1,
                       COUNT(*),
                       COALESCE(SUM(o.item_count),0),
                       SUM(CASE WHEN o.member_id IS NOT NULL THEN 1 ELSE 0 END),
                       COALESCE(SUM(o.total_amount),0),
                       COALESCE(SUM(o.discount_amount),0),
                       COALESCE(SUM(o.payable_amount),0),
                       COALESCE(SUM(CASE WHEN o.status = 'REFUNDED' THEN o.payable_amount ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN p.channel_code = 'CASH' THEN o.payable_amount ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN p.channel_code = 'WECHAT' THEN o.payable_amount ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN p.channel_code = 'ALIPAY' THEN o.payable_amount ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN p.channel_code = 'WALLET' THEN o.payable_amount ELSE 0 END),0),
                       COALESCE(SUM((SELECT SUM(i.cost_price * i.qty) FROM so_order_item i
                                      WHERE i.order_no = o.order_no)),0),
                       NOW()
                  FROM so_order o
                  LEFT JOIN pay_payment p ON p.order_no = o.order_no AND p.status = 'SUCCESS'
                 WHERE o.status IN ('PAID','COMPLETED') AND o.created_at >= ?
                 GROUP BY DATE(o.created_at)
                ON DUPLICATE KEY UPDATE
                    order_count = VALUES(order_count), item_count = VALUES(item_count),
                    member_order_count = VALUES(member_order_count),
                    total_amount = VALUES(total_amount), discount_amount = VALUES(discount_amount),
                    net_amount = VALUES(net_amount), refund_amount = VALUES(refund_amount),
                    cash_amount = VALUES(cash_amount), wechat_amount = VALUES(wechat_amount),
                    alipay_amount = VALUES(alipay_amount), wallet_amount = VALUES(wallet_amount),
                    cost_amount = VALUES(cost_amount), updated_at = NOW()
                """, from);
        return Map.of("rebuiltDays", days, "affectedRows", rows);
    }

    // ============================================================
    //  内部
    // ============================================================

    private Map<String, Object> oneDay(int offset) {
        String sql = """
                SELECT COUNT(*) AS orderCount,
                       COALESCE(SUM(payable_amount),0) AS netAmount,
                       COALESCE(SUM(total_amount),0) AS totalAmount,
                       COALESCE(SUM(discount_amount),0) AS discountAmount,
                       COALESCE(SUM(item_count),0) AS itemCount,
                       COALESCE(SUM(CASE WHEN member_id IS NOT NULL THEN 1 ELSE 0 END),0) AS memberOrders
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL ? DAY)
                """;
        Map<String, Object> row = jdbc.queryForMap(sql, offset);
        Map<String, Object> r = new LinkedHashMap<>(row);
        long net = toLong(row.get("netAmount"));
        long cnt = toLong(row.get("orderCount"));
        r.put("avgTicket", cnt == 0 ? 0 : net / cnt);
        return r;
    }

    private Map<String, Object> oneRange(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS orderCount,
                       COALESCE(SUM(payable_amount),0) AS netAmount,
                       COALESCE(SUM(total_amount),0) AS totalAmount,
                       COALESCE(SUM(discount_amount),0) AS discountAmount,
                       COALESCE(SUM(item_count),0) AS itemCount,
                       COALESCE(SUM(CASE WHEN member_id IS NOT NULL THEN 1 ELSE 0 END),0) AS memberOrders
                  FROM so_order
                 WHERE status IN ('PAID','COMPLETED') AND created_at >= ?
                """, from);
        Map<String, Object> r = new LinkedHashMap<>(row);
        long net = toLong(row.get("netAmount"));
        long cnt = toLong(row.get("orderCount"));
        r.put("avgTicket", cnt == 0 ? 0 : net / cnt);

        Long cost = jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.cost_price * i.qty),0)
                  FROM so_order_item i JOIN so_order o ON o.order_no = i.order_no
                 WHERE o.status IN ('PAID','COMPLETED') AND o.created_at >= ?
                """, Long.class, from);
        r.put("costAmount", cost == null ? 0 : cost);
        r.put("grossProfit", net - (cost == null ? 0 : cost));
        return r;
    }

    private long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
