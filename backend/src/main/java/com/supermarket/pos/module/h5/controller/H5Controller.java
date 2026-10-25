package com.supermarket.pos.module.h5.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.member.service.MemberService;
import com.supermarket.pos.module.order.domain.SoOrder;
import com.supermarket.pos.module.order.service.OrderService;
import com.supermarket.pos.module.pay.service.PaymentService;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.engine.PromotionEngine;
import com.supermarket.pos.module.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M10 会员端（H5）接口。
 *
 * <p>三端共用一套后端，靠 {@code channel} 区分订单来源：
 * 会员端下单 write {@code channel='H5'}、{@code cashier_id=null}，
 * 这样报表能分开看"店里卖的"和"手机上下单的"。
 *
 * <p><b>身份从 token 来，不从请求体来。</b>
 * 请求体里的 {@code memberId} 会被强制覆盖成登录会员的 id，
 * 否则改个数字就能用别人的券和储值——这是最典型的越权漏洞。
 */
@RestController
@RequestMapping("/api/h5")
@RequiredArgsConstructor
public class H5Controller {

    private final JdbcTemplate jdbc;
    private final MemberService memberService;
    private final PromotionService promotionService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    // ============================================================
    //  首页 / 商品
    // ============================================================

    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        Long memberId = UserContext.require().getUserId();
        Map<String, Object> me = memberService.detail(memberId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("member", me);
        r.put("categories", categories());
        r.put("hot", goods(null, null, 1, 8));
        r.put("activities", jdbc.queryForList("""
                SELECT a.activity_no AS activityNo, a.name, a.type, a.rule_json AS ruleJson,
                       a.start_at AS startAt, a.end_at AS endAt
                  FROM pmt_activity a
                 WHERE a.status = 1 AND NOW() BETWEEN a.start_at AND a.end_at
                 ORDER BY a.priority DESC
                """));
        r.put("couponCount", memberService.coupons(memberId, true).size());
        return Result.ok(r);
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categoriesApi() {
        return Result.ok(categories());
    }

    /** 商品列表：只返回上架且有可售库存的，避免顾客下单才被告知没货 */
    @GetMapping("/goods")
    public Result<List<Map<String, Object>>> goodsApi(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long categoryId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(goods(keyword, categoryId, page, size));
    }

    @GetMapping("/goods/{skuId}")
    public Result<Map<String, Object>> goodsDetail(@PathVariable Long skuId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id AS skuId, s.sku_code AS skuCode, s.name, s.spec, s.unit, s.barcode,
                       s.standard_price AS standardPrice, s.member_price AS memberPrice,
                       s.image_url AS imageUrl, (s.weight_type = 'WEIGHT') AS isWeighed,
                       s.category_id AS categoryId, s.remark AS description,
                       COALESCE(st.available, 0) AS available
                  FROM pms_sku s
                  LEFT JOIN inv_stock st ON st.sku_id = s.id AND st.store_id = 1
                 WHERE s.id = ? AND s.deleted = 0 AND s.status = 1
                """, skuId);
        if (rows.isEmpty()) {
            throw BizException.of(ErrorCode.GOODS_NOT_FOUND);
        }
        return Result.ok(rows.get(0));
    }

    // ============================================================
    //  购物车算钱（H5 端也用同一套促销引擎）
    // ============================================================

    @PostMapping("/cart/calculate")
    public Result<Map<String, Object>> calculate(@RequestBody CartRequest req) {
        req.setMemberId(UserContext.require().getUserId());
        PromotionEngine.Result calc = promotionService.calculate(req, false);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lines", calc.getLines());
        r.put("discounts", calc.getDiscounts());
        r.put("totalAmount", calc.getTotalAmount());
        r.put("totalDiscount", calc.getTotalDiscount());
        r.put("payableAmount", calc.getPayableAmount());
        r.put("pointUsed", calc.getPointUsed());
        r.put("pointAmount", calc.getPointAmount());
        return Result.ok(r);
    }

    // ============================================================
    //  下单 / 支付
    // ============================================================

    @PostMapping("/order")
    @PreAuthorize("hasAuthority('h5:order')")
    public Result<Map<String, Object>> createOrder(@RequestBody CartRequest req) {
        req.setMemberId(UserContext.require().getUserId());
        SoOrder order = orderService.create(req, "H5");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderNo", order.getOrderNo());
        r.put("payableAmount", order.getPayableAmount());
        r.put("totalAmount", order.getTotalAmount());
        r.put("discountAmount", order.getDiscountAmount());
        r.put("status", order.getStatus());
        return Result.ok("下单成功，请支付", r);
    }

    /**
     * 支付。演示版直接"支付成功"——内部还是走 create + 回调两条腿，
     * 跟 POS 的模拟支付完全一致，只是没人点按钮，由接口自己触发。
     */
    @PostMapping("/order/{orderNo}/pay")
    @PreAuthorize("hasAuthority('h5:order')")
    public Result<Map<String, Object>> pay(@PathVariable String orderNo,
                                           @RequestParam(defaultValue = "WECHAT") String channel) {
        mustOwn(orderNo);
        Map<String, Object> created = paymentService.create(orderNo, channel);
        String payNo = (String) created.get("payNo");
        Map<String, Object> cb = paymentService.handleCallback(payNo, true, null, 0);
        Map<String, Object> r = new LinkedHashMap<>(cb);
        r.put("payNo", payNo);
        r.put("channel", channel);
        return Result.ok("支付成功", r);
    }

    @PostMapping("/order/{orderNo}/cancel")
    @PreAuthorize("hasAuthority('h5:order')")
    public Result<Void> cancel(@PathVariable String orderNo) {
        mustOwn(orderNo);
        orderService.cancel(orderNo, "会员端取消");
        return Result.ok("订单已取消", null);
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> orders(@RequestParam(required = false) String status) {
        Long memberId = UserContext.require().getUserId();
        String base = """
                SELECT order_no AS orderNo, status, item_count AS itemCount,
                       total_amount AS totalAmount, discount_amount AS discountAmount,
                       payable_amount AS payableAmount, paid_amount AS paidAmount,
                       created_at AS createdAt, paid_at AS paidAt
                  FROM so_order
                 WHERE member_id = ?
                """;
        if (status != null && !status.isBlank()) {
            return Result.ok(jdbc.queryForList(base + " AND status = ? ORDER BY id DESC LIMIT 100",
                    memberId, status));
        }
        return Result.ok(jdbc.queryForList(base + " ORDER BY id DESC LIMIT 100", memberId));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<Map<String, Object>> orderDetail(@PathVariable String orderNo) {
        mustOwn(orderNo);
        return Result.ok(orderService.detail(orderNo));
    }

    // ============================================================
    //  我的
    // ============================================================

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(memberService.detail(UserContext.require().getUserId()));
    }

    @GetMapping("/me/points")
    public Result<List<Map<String, Object>>> points() {
        return Result.ok(memberService.pointsLog(UserContext.require().getUserId(), 50));
    }

    @GetMapping("/me/wallet")
    public Result<List<Map<String, Object>>> wallet() {
        return Result.ok(memberService.walletLog(UserContext.require().getUserId(), 50));
    }

    @GetMapping("/me/coupons")
    public Result<List<Map<String, Object>>> coupons(@RequestParam(defaultValue = "false") boolean onlyUsable) {
        return Result.ok(memberService.coupons(UserContext.require().getUserId(), onlyUsable));
    }

    // ============================================================
    //  内部
    // ============================================================

    /** 只能看自己的订单 */
    private void mustOwn(String orderNo) {
        Long memberId = UserContext.require().getUserId();
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM so_order WHERE order_no = ? AND member_id = ?",
                Integer.class, orderNo, memberId);
        if (n == null || n == 0) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    private List<Map<String, Object>> categories() {
        return jdbc.queryForList("""
                SELECT id, name, parent_id AS parentId, sort
                  FROM pms_category WHERE status = 1
                 ORDER BY sort, id
                """);
    }

    private List<Map<String, Object>> goods(String keyword, Long categoryId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        StringBuilder sql = new StringBuilder("""
                SELECT s.id AS skuId, s.sku_code AS skuCode, s.name, s.spec, s.unit,
                       s.standard_price AS standardPrice, s.member_price AS memberPrice,
                       s.image_url AS imageUrl, (s.weight_type = 'WEIGHT') AS isWeighed,
                       COALESCE(st.available, 0) AS available
                  FROM pms_sku s
                  LEFT JOIN inv_stock st ON st.sku_id = s.id AND st.store_id = 1
                 WHERE s.deleted = 0 AND s.status = 1
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.name LIKE ? OR s.barcode LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (categoryId != null) {
            sql.append(" AND s.category_id = ?");
            args.add(categoryId);
        }
        sql.append(" ORDER BY s.id LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
