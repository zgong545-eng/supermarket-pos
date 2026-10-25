package com.supermarket.pos.module.promotion.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.engine.PromotionEngine;
import com.supermarket.pos.module.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** M05 促销接口 */
@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final JdbcTemplate jdbc;

    /**
     * 算钱：收银台每扫一次码都会调一次。
     * 返回逐行金额 + 每一项优惠的来源和分摊明细。
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyAuthority('pos:checkout','order:view')")
    public Result<PromotionEngine.Result> calculate(@RequestBody CartRequest req) {
        return Result.ok(promotionService.calculate(req));
    }

    @GetMapping("/activities")
    @PreAuthorize("hasAuthority('promo:view')")
    public Result<List<Map<String, Object>>> activities() {
        return Result.ok(jdbc.queryForList("""
                SELECT a.id, a.activity_no AS activityNo, a.name, a.type, a.priority, a.status,
                       a.start_at AS startAt, a.end_at AS endAt, a.rule_json AS ruleJson,
                       a.remark, a.created_at AS createdAt,
                       (SELECT COUNT(*) FROM pmt_activity_sku s WHERE s.activity_id = a.id) AS skuCount
                  FROM pmt_activity a ORDER BY a.id DESC
                """));
    }

    @PostMapping("/activities")
    @PreAuthorize("hasAuthority('promo:edit')")
    public Result<Long> createActivity(@RequestBody Map<String, Object> body) {
        String activityNo = String.valueOf(body.getOrDefault("activityNo", ""));
        if (activityNo.isBlank()) {
            activityNo = "ACT" + System.currentTimeMillis();
        }
        jdbc.update("""
                INSERT INTO pmt_activity (activity_no, name, type, start_at, end_at, status, priority, rule_json, remark)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, activityNo,
                body.get("name"),
                body.get("type"),
                body.get("startAt"),
                body.get("endAt"),
                body.getOrDefault("status", 1),
                body.getOrDefault("priority", 100),
                body.getOrDefault("ruleJson", "{}"),
                body.get("remark"));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Object skus = body.get("skuIds");
        if (skus instanceof List<?> list) {
            for (Object o : list) {
                jdbc.update("INSERT IGNORE INTO pmt_activity_sku (activity_id, sku_id) VALUES (?,?)",
                        id, Long.valueOf(String.valueOf(o)));
            }
        }
        return Result.ok("活动已创建", id);
    }

    @PutMapping("/activities/{id}/status")
    @PreAuthorize("hasAuthority('promo:edit')")
    public Result<Void> toggleActivity(@PathVariable Long id, @RequestParam int status) {
        jdbc.update("UPDATE pmt_activity SET status = ? WHERE id = ?", status, id);
        return Result.ok();
    }

    @GetMapping("/coupons")
    @PreAuthorize("hasAuthority('promo:view')")
    public Result<List<Map<String, Object>>> coupons() {
        return Result.ok(jdbc.queryForList("""
                SELECT id, coupon_code AS couponCode, name, type, face_value AS faceValue,
                       min_amount AS minAmount, total_qty AS totalQty, issued_qty AS issuedQty,
                       per_limit AS perLimit, start_at AS startAt, end_at AS endAt,
                       valid_days AS validDays, status
                  FROM pmt_coupon ORDER BY id DESC
                """));
    }

    @PostMapping("/coupons")
    @PreAuthorize("hasAuthority('promo:coupon')")
    public Result<Long> createCoupon(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("couponCode", ""));
        if (code.isBlank()) {
            code = "CP" + System.currentTimeMillis();
        }
        jdbc.update("""
                INSERT INTO pmt_coupon
                    (coupon_code, name, type, face_value, min_amount, total_qty, issued_qty,
                     per_limit, start_at, end_at, valid_days, status)
                VALUES (?,?,?,?,?,?,0,?,?,?,?,1)
                """, code,
                body.get("name"),
                body.get("type"),
                body.getOrDefault("faceValue", 0),
                body.getOrDefault("minAmount", 0),
                body.getOrDefault("totalQty", 0),
                body.getOrDefault("perLimit", 1),
                body.getOrDefault("startAt", java.time.LocalDateTime.now()),
                body.getOrDefault("endAt", java.time.LocalDateTime.now().plusMonths(1)),
                body.getOrDefault("validDays", 0));
        return Result.ok("券已创建", jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }
}
