package com.supermarket.pos.module.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 课堂演示数据。
 *
 * <p>这套系统是单店自治、零外部集成，所以演示数据必须自己造。
 * 这个类解决三个真实痛点：
 * <ol>
 *   <li><b>报表全是 0</b>：新建的库没有历史订单，曲线图一片空白，演示很尴尬。
 *       用 {@code seedOrders} 生成跨 30 天的历史订单。</li>
 *   <li><b>数据被演示脏了</b>：反复下单、退款后库存和单据都乱了。
 *       用 {@code reset} 一键回到干净状态。</li>
 *   <li><b>没有商品可卖</b>：空库连扫码都扫不出东西。
 *       用 {@code seedBase} 铺 60 个 SKU + 3 个会员 + 2 个活动 + 3 种券。</li>
 * </ol>
 *
 * <p>安全注意：这一组接口只在 {@code pos.demo.enabled=true} 时可用，
 * 真实环境必须关掉，否则任何人都能清库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private final JdbcTemplate jdbc;

    @Value("${pos.demo.enabled:false}")
    private boolean demoEnabled;

    private final Random random = new Random(20261025L);   // 固定种子：每次生成的数据一样，方便复现

    // ============================================================
    //  重置
    // ============================================================

    /**
     * 一键回到干净状态：清业务数据 + 重铺基础演示数据。
     * 不删用户、角色、权限、字典——那些是系统配置，不属于业务数据。
     */
    @Transactional
    public Map<String, Object> reset() {
        assertEnabled();

        List<String> businessTables = List.of(
                "so_order_status_log", "so_order_discount", "so_order_item", "so_order",
                "pay_refund", "pay_payment",
                "pos_receipt", "pos_hold",
                "inv_stock_log", "stock_bill_item", "stock_bill", "inv_stock",
                "mem_point_log", "mem_wallet_txn", "mem_wallet", "mem_member_coupon", "mem_member",
                "rpt_daily_sales", "rpt_sku_sales",
                "pms_price_change", "pms_sku_barcode", "pms_sku",
                "pmt_activity_sku", "pmt_activity", "pmt_coupon_sku", "pmt_coupon",
                "sys_sequence", "pos_shift"
        );
        for (String t : businessTables) {
            jdbc.execute("TRUNCATE TABLE " + t);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cleared", businessTables.size());
        r.put("seeded", seedBase());
        log.info("演示数据已重置，清理 {} 张表", businessTables.size());
        return r;
    }

    // ============================================================
    //  铺基础数据：60 SKU / 3 会员 / 2 活动 / 3 券
    // ============================================================

    @Transactional
    public Map<String, Object> seedBase() {
        assertEnabled();

        // ---------- 60 个商品 ----------
        String[][] catalog = {
                // {名称, 分类id, 规格, 单位, 标准价(分), 会员价(分), 成本价(分), 条码尾号}
                {"伊利纯牛奶", "11", "250ml×12", "箱", "6900", "6600", "5800", "1001"},
                {"蒙牛特仑苏", "11", "250ml×12", "箱", "7900", "7500", "6600", "1002"},
                {"光明优倍鲜奶", "11", "950ml", "瓶", "2390", "2190", "1900", "1003"},
                {"安慕希酸奶", "11", "205g×6", "提", "3990", "3690", "3200", "1004"},
                {"简爱裸酸奶", "11", "135g×4", "组", "2690", "2490", "2100", "1005"},
                {"乐事薯片原味", "12", "70g", "袋", "890", "790", "600", "1006"},
                {"乐事薯片黄瓜", "12", "70g", "袋", "890", "790", "600", "1007"},
                {"奥利奥饼干", "12", "116g", "包", "1290", "1190", "950", "1008"},
                {"旺旺雪饼", "12", "84g", "包", "690", "620", "480", "1009"},
                {"德芙巧克力", "12", "43g", "条", "990", "890", "700", "1010"},
                {"农夫山泉", "13", "550ml", "瓶", "200", "180", "120", "1011"},
                {"怡宝纯净水", "13", "550ml", "瓶", "200", "180", "120", "1012"},
                {"可口可乐", "13", "500ml", "瓶", "350", "320", "240", "1013"},
                {"百事可乐", "13", "500ml", "瓶", "350", "320", "240", "1014"},
                {"元气森林气泡水", "13", "480ml", "瓶", "550", "500", "380", "1015"},
                {"青岛啤酒", "13", "500ml", "罐", "690", "650", "520", "1016"},
                {"张裕干红", "13", "750ml", "瓶", "8800", "8300", "7000", "1017"},
                {"九龙斋酸梅汤", "13", "500ml", "瓶", "590", "550", "420", "1018"},
                {"上海青", "21", "500g", "份", "350", "320", "220", "1019"},
                {"西兰花", "21", "500g", "份", "590", "540", "400", "1020"},
                {"土豆", "21", "1kg", "份", "480", "440", "320", "1021"},
                {"西红柿", "21", "500g", "份", "550", "500", "380", "1022"},
                {"黄瓜", "21", "500g", "份", "390", "350", "260", "1023"},
                {"胡萝卜", "21", "500g", "份", "320", "290", "210", "1024"},
                {"红富士苹果", "22", "1kg", "份", "1580", "1450", "1100", "1025"},
                {"香蕉", "22", "1kg", "份", "990", "890", "650", "1026"},
                {"阳光玫瑰葡萄", "22", "500g", "份", "2980", "2750", "2200", "1027"},
                {"赣南脐橙", "22", "1kg", "份", "1380", "1280", "980", "1028"},
                {"海南芒果", "22", "1kg", "份", "1680", "1550", "1200", "1029"},
                {"蓝莓", "22", "125g", "盒", "1990", "1850", "1500", "1030"},
                {"猪里脊", "23", "500g", "份", "2680", "2550", "2100", "1031"},
                {"五花肉", "23", "500g", "份", "2280", "2150", "1750", "1032"},
                {"牛腩", "23", "500g", "份", "4980", "4750", "4100", "1033"},
                {"鸡胸肉", "23", "500g", "份", "1580", "1450", "1150", "1034"},
                {"鸡蛋", "23", "30枚", "盒", "2590", "2450", "2100", "1035"},
                {"基围虾", "23", "500g", "份", "5980", "5700", "4900", "1036"},
                {"蓝月亮洗衣液", "31", "3kg", "瓶", "4590", "4290", "3600", "1037"},
                {"立白洗洁精", "31", "1.5kg", "瓶", "1990", "1850", "1500", "1038"},
                {"心相印抽纸", "31", "3层×24包", "提", "4990", "4690", "3900", "1039"},
                {"洁柔卷纸", "31", "10卷", "提", "2990", "2790", "2300", "1040"},
                {"威露士消毒液", "31", "1.5L", "瓶", "3590", "3350", "2800", "1041"},
                {"舒肤佳香皂", "31", "115g×3", "组", "1690", "1580", "1250", "1042"},
                {"云南白药牙膏", "31", "120g", "支", "2280", "2150", "1750", "1043"},
                {"海飞丝洗发水", "31", "400ml", "瓶", "3990", "3750", "3100", "1044"},
                {"金龙鱼大米", "41", "5kg", "袋", "4990", "4690", "4100", "1045"},
                {"五常稻花香", "41", "5kg", "袋", "7990", "7590", "6600", "1046"},
                {"福临门面粉", "41", "5kg", "袋", "2990", "2790", "2400", "1047"},
                {"挂面", "41", "1kg", "袋", "890", "820", "650", "1048"},
                {"海天生抽", "42", "1.9L", "瓶", "1990", "1850", "1450", "1049"},
                {"海天蚝油", "42", "700g", "瓶", "1590", "1480", "1150", "1050"},
                {"李锦记蒸鱼豉油", "42", "410ml", "瓶", "1290", "1190", "950", "1051"},
                {"老干妈辣酱", "42", "280g", "瓶", "1090", "990", "780", "1052"},
                {"太太乐鸡精", "42", "200g", "袋", "890", "820", "640", "1053"},
                {"恒顺香醋", "42", "500ml", "瓶", "790", "720", "550", "1054"},
                {"中盐加碘盐", "42", "400g", "袋", "290", "270", "180", "1055"},
                {"太古白砂糖", "42", "400g", "袋", "590", "540", "420", "1056"},
                {"橄榄油", "42", "500ml", "瓶", "6890", "6490", "5600", "1057"},
                {"花椒", "42", "50g", "袋", "690", "640", "480", "1058"},
                {"八角", "42", "50g", "袋", "790", "730", "560", "1059"},
                {"黑胡椒粒", "42", "40g", "瓶", "1290", "1190", "950", "1060"}
        };

        int skuCount = 0;
        for (int i = 0; i < catalog.length; i++) {
            String[] c = catalog[i];
            long standard = Long.parseLong(c[4]);
            long member = Long.parseLong(c[5]);
            long cost = Long.parseLong(c[6]);
            String barcode = "69000000" + c[7];

            jdbc.update("""
                    INSERT INTO pms_sku
                        (sku_code, barcode, name, category_id, spec, unit, brand,
                         standard_price, member_price, cost_price, weight_type, status, remark)
                    VALUES (?,?,?,?,?,?,'山姆精选',?,?,?,'PIECE',1,?)
                    """,
                    "SKU" + c[7], barcode, c[0], Long.parseLong(c[1]), c[2], c[3],
                    standard, member, cost, "演示数据");
            Long skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            jdbc.update("INSERT IGNORE INTO pms_sku_barcode (sku_id, barcode, type) VALUES (?,?,'NORMAL')",
                    skuId, barcode);

            // 库存：100-500 之间随机，其中 5 个刻意压到安全线以下，用来演示补货
            int qty = 100 + random.nextInt(400);
            if (i < 5) {
                qty = random.nextInt(8);
            }
            jdbc.update("""
                    INSERT INTO inv_stock (sku_id, store_id, on_hand, available, locked, safety_stock, version, updated_at)
                    VALUES (?, 1, ?, ?, 0, 20, 0, NOW())
                    """, skuId, qty, qty);
            skuCount++;
        }

        // ---------- 3 个会员 ----------
        String[][] members = {
                {"张伟", "13900000001", "3"},   // 金卡
                {"李娜", "13900000002", "2"},   // 银卡
                {"王强", "13900000003", "1"}    // 普通
        };
        for (String[] m : members) {
            jdbc.update("""
                    INSERT INTO mem_member (member_no, name, phone, gender, level_id, points, total_points,
                                            status, register_store_id, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, 1, ?, ?, ?, 1, 1, NOW(), NOW(), 0)
                    """, "M" + m[1].substring(7), m[0], m[1], Long.parseLong(m[2]),
                    Integer.parseInt(m[2]) * 1500, Integer.parseInt(m[2]) * 1500);
            Long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO mem_wallet (member_id, balance, version, updated_at) VALUES (?,?,0,NOW())",
                    memberId, "3".equals(m[2]) ? 200000L : 50000L);
        }

        // ---------- 2 个活动 ----------
        jdbc.update("""
                INSERT INTO pmt_activity (activity_no, name, type, start_at, end_at, status, priority, rule_json, remark)
                VALUES ('ACT20261025001', '满 100 减 10', 'FULL_REDUCE',
                        DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), 1, 10,
                        '{"threshold":10000,"reduce":1000,"repeat":true}', '全场通用，每满 100 减 10')
                """);
        Long act1 = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO pmt_activity (activity_no, name, type, start_at, end_at, status, priority, rule_json, remark)
                VALUES ('ACT20261025002', '乳品 9 折', 'DISCOUNT',
                        DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), 1, 20,
                        '{"rate":90}', '乳品类专享 9 折')
                """);
        Long act2 = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (Long skuId : jdbc.queryForList(
                "SELECT id FROM pms_sku WHERE category_id = 11", Long.class)) {
            jdbc.update("INSERT IGNORE INTO pmt_activity_sku (activity_id, sku_id) VALUES (?,?)",
                    act2, skuId);
        }

        // ---------- 3 种券 ----------
        jdbc.update("""
                INSERT INTO pmt_coupon (coupon_code, name, type, face_value, min_amount, total_qty, issued_qty,
                                        per_limit, start_at, end_at, valid_days, status)
                VALUES ('CP-NEW-20', '新人 20 元券', 'CASH', 2000, 10000, 1000, 0, 1,
                        DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), 30, 1)
                """);
        jdbc.update("""
                INSERT INTO pmt_coupon (coupon_code, name, type, face_value, min_amount, total_qty, issued_qty,
                                        per_limit, start_at, end_at, valid_days, status)
                VALUES ('CP-50-500', '满 500 减 50', 'CASH', 5000, 50000, 500, 0, 2,
                        DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), 0, 1)
                """);
        jdbc.update("""
                INSERT INTO pmt_coupon (coupon_code, name, type, face_value, min_amount, total_qty, issued_qty,
                                        per_limit, start_at, end_at, valid_days, status)
                VALUES ('CP-95-OFF', '全场 95 折', 'DISCOUNT', 95, 3000, 500, 0, 1,
                        DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), 0, 1)
                """);

        // 给三个会员各发两张券
        for (Long memberId : jdbc.queryForList("SELECT id FROM mem_member", Long.class)) {
            jdbc.update("""
                    INSERT INTO mem_member_coupon (member_id, coupon_id, status, receive_at, expire_at)
                    SELECT ?, id, 'UNUSED', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)
                      FROM pmt_coupon WHERE coupon_code IN ('CP-NEW-20','CP-95-OFF')
                    """, memberId);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sku", skuCount);
        r.put("member", members.length);
        r.put("activity", 2);
        r.put("coupon", 3);
        log.info("演示基础数据已铺：{} 个 SKU、{} 个会员", skuCount, members.length);
        return r;
    }

    // ============================================================
    //  生成历史订单（喂报表）
    // ============================================================

    /**
     * 生成跨 N 天的历史已完成订单。
     *
     * <p>只写订单与支付记录，<b>不动库存</b>——历史订单不应该影响当前库存，
     * 否则演示时"账面库存"和"刚补的货"对不上，反而解释不清。
     * 报表数据同时写入预聚合表，保证图表打开就有内容。
     */
    @Transactional
    public Map<String, Object> seedOrders(int days, int totalOrders) {
        assertEnabled();
        days = Math.max(1, Math.min(days, 90));
        totalOrders = Math.max(1, Math.min(totalOrders, 2000));

        List<Map<String, Object>> skus = jdbc.queryForList(
                "SELECT id, sku_code, name, barcode, unit, standard_price, cost_price FROM pms_sku WHERE deleted = 0");
        List<Long> members = jdbc.queryForList("SELECT id FROM mem_member", Long.class);
        // 收银员 id 也从库里查，避免写死
        Long cashierId = jdbc.query("SELECT id FROM sys_user WHERE username = 'cashier' LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (skus.isEmpty()) {
            throw new IllegalStateException("请先调用 /api/demo/seed 铺商品数据");
        }

        // 按天聚合，最后一次性写 rpt 表
        Map<LocalDate, long[]> daily = new LinkedHashMap<>();   // {订单数, 件数, 会员单数, 原价, 优惠, 实收, 现金, 微信, 支付宝, 储值, 成本}
        Map<String, long[]> skuAgg = new LinkedHashMap<>();     // key = date|skuId

        int perDay = Math.max(1, totalOrders / days);
        LocalDate today = LocalDate.now();
        int created = 0;

        for (int d = days - 1; d >= 0; d--) {
            LocalDate date = today.minusDays(d);
            for (int k = 0; k < perDay; k++) {
                LocalDateTime ts = date.atTime(9 + random.nextInt(12), random.nextInt(60), random.nextInt(60));

                int lineCount = 1 + random.nextInt(5);
                long totalAmount = 0;
                long totalCost = 0;
                int itemCount = 0;
                List<long[]> lines = new ArrayList<>();   // {skuId, qty, price, cost, name hash}

                for (int i = 0; i < lineCount; i++) {
                    Map<String, Object> sku = skus.get(random.nextInt(skus.size()));
                    long skuId = ((Number) sku.get("id")).longValue();
                    long price = ((Number) sku.get("standard_price")).longValue();
                    long cost = ((Number) sku.get("cost_price")).longValue();
                    int qty = 1 + random.nextInt(3);
                    totalAmount += price * qty;
                    totalCost += cost * qty;
                    itemCount += qty;
                    lines.add(new long[]{skuId, qty, price, cost});
                }

                // 会员单约 45%
                Long memberId = (!members.isEmpty() && random.nextDouble() < 0.45)
                        ? members.get(random.nextInt(members.size())) : null;
                long discount = memberId != null ? totalAmount * random.nextInt(15) / 100 : 0;
                long payable = Math.max(0, totalAmount - discount);

                String channel = switch (random.nextInt(10)) {
                    case 0, 1, 2, 3 -> "WECHAT";
                    case 4, 5 -> "ALIPAY";
                    case 6, 7, 8 -> "CASH";
                    default -> "WALLET";
                };

                String orderNo = "SO" + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                        + String.format("%05d", created + 1);

                // cashier_id 从库里查，不写死数字——换个库 id 就变了
                jdbc.update("""
                        INSERT INTO so_order
                            (order_no, store_id, member_id, cashier_id, channel, status, item_count,
                             total_amount, discount_amount, payable_amount, paid_amount, change_amount,
                             paid_at, finished_at, created_at, updated_at, version)
                        VALUES (?,1,?,?,'POS','COMPLETED',?,?,?,?,?,0,?,?,?,?,0)
                        """, orderNo, memberId, cashierId, itemCount, totalAmount, discount, payable,
                        payable, ts, ts, ts, ts);

                int lineNo = 1;
                for (long[] l : lines) {
                    jdbc.update("""
                            INSERT INTO so_order_item
                                (order_no, line_no, sku_id, sku_code, sku_name, unit,
                                 standard_price, sale_price, qty, amount, discount_amount, net_amount,
                                 member_price_used, cost_price, created_at)
                            SELECT ?,?,?,sku_code,name,unit,?,?,?,?,0,?,0,?,?
                              FROM pms_sku WHERE id = ?
                            """, orderNo, lineNo++, l[0], l[2], l[2], l[1], l[2] * l[1],
                            l[2] * l[1], l[3], ts, l[0]);

                    String key = date + "|" + l[0];
                    long[] agg = skuAgg.computeIfAbsent(key, x -> new long[3]);
                    agg[0] = l[0];                 // skuId
                    agg[1] += l[1];                // qty
                    agg[2] += l[2] * l[1];         // amount
                }

                if (discount > 0) {
                    jdbc.update("""
                            INSERT INTO so_order_discount (order_no, seq, source_type, name, amount, created_at)
                            VALUES (?, 1, 'ACTIVITY', '会员优惠', ?, ?)
                            """, orderNo, discount, ts);
                }

                jdbc.update("""
                        INSERT INTO pay_payment (pay_no, order_no, channel_code, amount, status,
                                                 channel_trade_no, callback_count, paid_at, created_at, updated_at)
                        VALUES (?,?,?,?,'SUCCESS',?,1,?,?,?)
                        """, "PY" + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                                + String.format("%05d", created + 1),
                        orderNo, channel, payable, "HIS" + orderNo, ts, ts, ts);

                long[] day = daily.computeIfAbsent(date, x -> new long[11]);
                day[0] += 1;
                day[1] += itemCount;
                day[2] += memberId != null ? 1 : 0;
                day[3] += totalAmount;
                day[4] += discount;
                day[5] += payable;
                switch (channel) {
                    case "CASH" -> day[6] += payable;
                    case "WECHAT" -> day[7] += payable;
                    case "ALIPAY" -> day[8] += payable;
                    case "WALLET" -> day[9] += payable;
                    default -> { }
                }
                day[10] += totalCost;

                created++;
            }
        }

        // ---------- 写预聚合表 ----------
        daily.forEach((date, v) -> {
            jdbc.update("""
                    INSERT INTO rpt_daily_sales
                        (stat_date, store_id, order_count, item_count, member_order_count,
                         total_amount, discount_amount, net_amount, refund_amount,
                         cash_amount, wechat_amount, alipay_amount, wallet_amount, cost_amount, updated_at)
                    VALUES (?,1,?,?,?,?,?,?,0,?,?,?,?,?,NOW())
                    ON DUPLICATE KEY UPDATE
                        order_count = VALUES(order_count), item_count = VALUES(item_count),
                        member_order_count = VALUES(member_order_count), total_amount = VALUES(total_amount),
                        discount_amount = VALUES(discount_amount), net_amount = VALUES(net_amount),
                        cash_amount = VALUES(cash_amount), wechat_amount = VALUES(wechat_amount),
                        alipay_amount = VALUES(alipay_amount), wallet_amount = VALUES(wallet_amount),
                        cost_amount = VALUES(cost_amount), updated_at = NOW()
                    """, date, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10]);
        });

        skuAgg.forEach((key, v) -> {
            String[] parts = key.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String skuName = jdbc.queryForObject(
                    "SELECT name FROM pms_sku WHERE id = ?", String.class, v[0]);
            jdbc.update("""
                    INSERT INTO rpt_sku_sales (stat_date, store_id, sku_id, sku_name, qty, amount)
                    VALUES (?,1,?,?,?,?)
                    ON DUPLICATE KEY UPDATE qty = qty + VALUES(qty), amount = amount + VALUES(amount)
                    """, date, v[0], skuName, v[1], v[2]);
        });

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orders", created);
        r.put("days", days);
        r.put("dailyRows", daily.size());
        log.info("已生成 {} 笔历史订单，覆盖 {} 天", created, days);
        return r;
    }

    /** 演示数据现状，页面上可以直接显示 */
    public Map<String, Object> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("demoEnabled", demoEnabled);
        r.put("sku", jdbc.queryForObject("SELECT COUNT(*) FROM pms_sku WHERE deleted = 0", Integer.class));
        r.put("member", jdbc.queryForObject("SELECT COUNT(*) FROM mem_member WHERE deleted = 0", Integer.class));
        r.put("order", jdbc.queryForObject("SELECT COUNT(*) FROM so_order", Integer.class));
        r.put("warning", jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_stock WHERE available < safety_stock", Integer.class));
        return r;
    }

    private void assertEnabled() {
        if (!demoEnabled) {
            throw new IllegalStateException("演示接口未启用（pos.demo.enabled=false）");
        }
    }
}
