package com.supermarket.pos.module.order.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.module.order.domain.SoOrder;
import com.supermarket.pos.module.order.service.OrderService;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** M06 订单接口 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单：算钱 + 预占库存，返回待支付订单 */
    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('pos:checkout','order:view')")
    public Result<SoOrder> create(@RequestBody CartRequest req,
                                  @RequestParam(defaultValue = "POS") String channel) {
        return Result.ok("下单成功，等待支付", orderService.create(req, channel));
    }

    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAuthority('order:view')")
    public Result<Map<String, Object>> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('order:view')")
    public Result<List<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(orderService.page(keyword, status, page, size));
    }

    @PostMapping("/{orderNo}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestParam(required = false) String reason) {
        orderService.cancel(orderNo, reason == null ? "手工取消" : reason);
        return Result.ok("订单已取消，库存已释放", null);
    }

    /** 整单退货：退钱 + 库存回库 + 积分回滚 + 券退回 */
    @PostMapping("/{orderNo}/refund")
    @PreAuthorize("hasAuthority('order:refund')")
    public Result<Void> refund(@PathVariable String orderNo,
                               @RequestParam(required = false) String reason) {
        orderService.refund(orderNo, UserContext.currentUserId(), reason == null ? "顾客退货" : reason);
        return Result.ok("退货完成", null);
    }

    @GetMapping("/today-summary")
    public Result<Map<String, Object>> todaySummary() {
        return Result.ok(orderService.todaySummary());
    }
}
