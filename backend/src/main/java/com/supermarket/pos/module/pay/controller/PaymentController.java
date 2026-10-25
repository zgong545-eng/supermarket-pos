package com.supermarket.pos.module.pay.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M07 支付接口。
 *
 * <p>{@code /mock-callback} 是课堂演示专用：没有真实微信支付对接，
 * 但回调之后走的逻辑和真实环境一模一样（含幂等）。
 * 这就是"入口可以伪造，账不能算错"。
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 发起支付，拿到支付单号 */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Map<String, Object>> create(@RequestParam String orderNo,
                                              @RequestParam String channel) {
        return Result.ok(paymentService.create(orderNo, channel));
    }

    /** 现金收款，一步到位并计算找零 */
    @PostMapping("/cash")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Map<String, Object>> cash(@RequestParam String orderNo,
                                            @RequestParam(required = false) Long tendered) {
        return Result.ok("收款成功", paymentService.cash(orderNo, tendered));
    }

    /**
     * 模拟支付回调。
     *
     * @param payNo     支付单号
     * @param success   是否成功
     * @param tradeNo   渠道流水号，重复传同一个值可以演示幂等
     * @param delayMs   故意延迟（演示"卡顿"），0-5000ms
     */
    @PostMapping("/mock-callback")
    public Result<Map<String, Object>> mockCallback(@RequestParam String payNo,
                                                    @RequestParam(defaultValue = "true") boolean success,
                                                    @RequestParam(required = false) String tradeNo,
                                                    @RequestParam(defaultValue = "0") long delayMs) {
        Map<String, Object> r = paymentService.handleCallback(payNo, success, tradeNo, delayMs);
        boolean accepted = Boolean.TRUE.equals(r.get("accepted"));
        return Result.ok(accepted ? "回调已处理" : "重复回调，已忽略", r);
    }

    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAnyAuthority('pay:view','order:view','pos:checkout')")
    public Result<List<Map<String, Object>>> byOrder(@PathVariable String orderNo) {
        return Result.ok(paymentService.byOrder(orderNo));
    }
}
