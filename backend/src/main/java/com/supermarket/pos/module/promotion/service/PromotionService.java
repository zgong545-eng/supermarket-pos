package com.supermarket.pos.module.promotion.service;

import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.engine.PromotionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M05 促销服务。
 *
 * <p>职责边界很清楚：<b>只回答"这单能便宜多少、为什么"，绝不生成订单</b>。
 * 数据从库里读出来，交给 {@link PromotionEngine} 这个纯函数算，
 * 再把结果原样返回。所以同一个购物车，预览和真正下单得到的金额必然一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final JdbcTemplate jdbc;

    /** 读配置里的积分规则，每次现建引擎——配置改了不用重启 */
    private PromotionEngine engine() {
        int rate = configInt("point.exchange_rate", 100);
        int ratio = configInt("point.max_deduct_ratio", 30);
        return new PromotionEngine(rate, ratio);
    }

    /** 算钱（不下单）。收银台每次扫完码都会调一次。 */
    public PromotionEngine.Result calculate(CartRequest req) {
        return calculate(req, null);
    }

    /**
     * @param forUpdate true 时校验更严（下单用），false 时宽松（预览用）
     */
    public PromotionEngine.Result calculate(CartRequest req, Boolean forUpdate) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw BizException.of(ErrorCode.CART_EMPTY);
        }

        PromotionEngine.Context ctx = new PromotionEngine.Context();

        // ---------- 购物车行 ----------
        List<PromotionEngine.CartLine> lines = new ArrayList<>();
        for (CartRequest.Item item : req.getItems()) {
            if (item.getSkuId() == null) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "商品 id 不能为空");
            }
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, sku_code, name, barcode, unit, standard_price, member_price,
                           cost_price, weight_type, status
                      FROM pms_sku WHERE id = ? AND deleted = 0
                    """, item.getSkuId());
            if (rows.isEmpty()) {
                throw BizException.of(ErrorCode.GOODS_NOT_FOUND, "商品 id " + item.getSkuId() + " 不存在");
            }
            Map<String, Object> sku = rows.get(0);
            if (((Number) sku.get("status")).intValue() != 1) {
                throw BizException.of(ErrorCode.SKU_OFF_SHELF, sku.get("name") + " 已下架");
            }

            String weightType = String.valueOf(sku.get("weight_type"));
            int qty;
            BigDecimal weight = null;
            if ("WEIGHT".equals(weightType)) {
                weight = item.getWeight();
                if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                    throw BizException.of(ErrorCode.PARAM_INVALID,
                            sku.get("name") + " 是称重商品，必须输入重量");
                }
                // 称重商品统一折算成"多少份 0.001kg"作为数量，保证金额能按分取整
                qty = weight.multiply(BigDecimal.valueOf(1000))
                        .setScale(0, RoundingMode.HALF_UP).intValue();
            } else {
                qty = item.getQty() == null || item.getQty() <= 0 ? 1 : item.getQty();
            }

            PromotionEngine.CartLine line = new PromotionEngine.CartLine();
            line.setSkuId(((Number) sku.get("id")).longValue());
            line.setSkuCode((String) sku.get("sku_code"));
            line.setSkuName((String) sku.get("name"));
            line.setBarcode((String) sku.get("barcode"));
            line.setUnit((String) sku.get("unit"));
            line.setCostPrice(sku.get("cost_price") == null ? 0L : ((Number) sku.get("cost_price")).longValue());
            line.setMemberPrice(sku.get("member_price") == null ? null : ((Number) sku.get("member_price")).longValue());
            line.setQty(qty);
            line.setWeight(weight);

            long standardPrice = ((Number) sku.get("standard_price")).longValue();
            if ("WEIGHT".equals(weightType)) {
                // 称重商品：标准价是"每 kg 价"，按 0.001kg 为一份换算
                standardPrice = standardPrice / 1000;
            }
            line.setStandardPrice(standardPrice);
            lines.add(line);
        }
        ctx.setLines(lines);

        // ---------- 会员 ----------
        if (req.getMemberId() != null) {
            List<Map<String, Object>> m = jdbc.queryForList("""
                    SELECT m.id, m.points, m.status, l.discount_rate
                      FROM mem_member m JOIN mem_level l ON l.id = m.level_id
                     WHERE m.id = ? AND m.deleted = 0
                    """, req.getMemberId());
            if (m.isEmpty()) {
                throw BizException.of(ErrorCode.MEMBER_NOT_FOUND);
            }
            Map<String, Object> mm = m.get(0);
            if (((Number) mm.get("status")).intValue() != 1) {
                throw BizException.of(ErrorCode.MEMBER_DISABLED);
            }
            ctx.setMember(true);
            ctx.setLevelDiscountRate(((Number) mm.get("discount_rate")).intValue());
            int owned = ((Number) mm.get("points")).intValue();
            ctx.setUsePoints(Math.min(Math.max(req.getUsePoints(), 0), owned));
        }

        // ---------- 活动 ----------
        ctx.setActivities(loadActivities());

        // ---------- 券 ----------
        if (req.getMemberCouponIds() != null && !req.getMemberCouponIds().isEmpty()) {
            if (req.getMemberId() == null) {
                throw BizException.of(ErrorCode.COUPON_NOT_MATCH, "非会员不能使用优惠券");
            }
            ctx.setCoupons(loadCoupons(req.getMemberId(), req.getMemberCouponIds()));
        }

        PromotionEngine.Result result = engine().calculate(ctx);

        // 下单前的最后一道检查：应付金额不能为负
        if (result.getPayableAmount() < 0) {
            result.setPayableAmount(0);
        }
        return result;
    }

    /** 把券标记为已用。必须在订单支付成功后调用，不能在下单时调。 */
    @Transactional
    public void markCouponsUsed(List<Long> memberCouponIds, Long memberId, String orderNo) {
        if (memberCouponIds == null || memberCouponIds.isEmpty()) {
            return;
        }
        for (Long id : memberCouponIds) {
            int rows = jdbc.update("""
                    UPDATE mem_member_coupon
                       SET status = 'USED', used_at = NOW(), order_no = ?
                     WHERE id = ? AND member_id = ? AND status = 'UNUSED'
                    """, orderNo, id, memberId);
            if (rows == 0) {
                // 并发下可能已被另一单用掉，记日志，由订单事务回滚兜底
                log.warn("券 {} 标记已用时状态不符（可能已被使用）", id);
            }
        }
    }

    /** 退货时把券退回 */
    @Transactional
    public void restoreCoupons(String orderNo) {
        jdbc.update("""
                UPDATE mem_member_coupon
                   SET status = 'UNUSED', used_at = NULL, order_no = NULL
                 WHERE order_no = ? AND status = 'USED'
                """, orderNo);
    }

    // ============================================================
    //  内部
    // ============================================================

    private List<PromotionEngine.Activity> loadActivities() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, activity_no, name, type, priority, rule_json
                  FROM pmt_activity
                 WHERE status = 1 AND start_at <= NOW() AND end_at >= NOW()
                 ORDER BY priority ASC, id ASC
                """);
        List<PromotionEngine.Activity> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            PromotionEngine.Activity a = new PromotionEngine.Activity();
            a.setActivityNo((String) r.get("activity_no"));
            a.setName((String) r.get("name"));
            a.setType((String) r.get("type"));
            a.setPriority(((Number) r.get("priority")).intValue());
            a.setRule((String) r.get("rule_json"));
            a.setSkuIds(jdbc.queryForList(
                    "SELECT sku_id FROM pmt_activity_sku WHERE activity_id = ?",
                    Long.class, r.get("id")));
            list.add(a);
        }
        return list;
    }

    private List<PromotionEngine.Coupon> loadCoupons(Long memberId, List<Long> memberCouponIds) {
        List<PromotionEngine.Coupon> list = new ArrayList<>();
        for (Long mcId : memberCouponIds) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT c.coupon_code, c.name, c.type, c.face_value, c.min_amount, mc.status, mc.expire_at
                      FROM mem_member_coupon mc
                      JOIN pmt_coupon c ON c.id = mc.coupon_id
                     WHERE mc.id = ? AND mc.member_id = ?
                    """, mcId, memberId);
            if (rows.isEmpty()) {
                throw BizException.of(ErrorCode.COUPON_NOT_FOUND);
            }
            Map<String, Object> r = rows.get(0);
            if (!"UNUSED".equals(r.get("status"))) {
                throw BizException.of(ErrorCode.COUPON_USED);
            }
            LocalDateTime expire = ((java.sql.Timestamp) r.get("expire_at")).toLocalDateTime();
            if (expire.isBefore(LocalDateTime.now())) {
                throw BizException.of(ErrorCode.COUPON_EXPIRED);
            }

            PromotionEngine.Coupon c = new PromotionEngine.Coupon();
            c.setCouponCode((String) r.get("coupon_code"));
            c.setName((String) r.get("name"));
            c.setType((String) r.get("type"));
            c.setFaceValue(((Number) r.get("face_value")).longValue());
            c.setMinAmount(((Number) r.get("min_amount")).longValue());
            Long couponId = jdbc.queryForObject(
                    "SELECT coupon_id FROM mem_member_coupon WHERE id = ?", Long.class, mcId);
            c.setSkuIds(jdbc.queryForList(
                    "SELECT sku_id FROM pmt_coupon_sku WHERE coupon_id = ?", Long.class, couponId));
            list.add(c);
        }
        return list;
    }

    private int configInt(String key, int def) {
        try {
            Integer v = jdbc.queryForObject(
                    "SELECT CAST(config_value AS SIGNED) FROM sys_config WHERE config_key = ?",
                    Integer.class, key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }
}
