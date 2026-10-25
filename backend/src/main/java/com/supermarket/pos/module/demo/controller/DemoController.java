package com.supermarket.pos.module.demo.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.demo.service.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示数据接口（仅在 {@code pos.demo.enabled=true} 时可用）。
 *
 * <p>课堂演示的死穴是"没数据"：新库打开报表页全是 0，讲不下去。
 * 所以留了这组接口，一键灌 60 个商品、3 个会员和 30 天历史订单。
 *
 * <p><b>安全提醒</b>：这些接口在 SecurityConfig 里被列入白名单（不等登录），
 * 生产环境必须把 {@code pos.demo.enabled} 关掉，否则任何人都能清库。
 * 开关默认 false，只有 {@code application-demo.yml} 打开。
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @Value("${pos.demo.enabled:false}")
    private boolean enabled;

    /** 全量重置：清空业务表 + 重建基础数据（商品/会员/活动/券） */
    @PostMapping("/reset")
    public Result<Map<String, Object>> reset() {
        guard();
        return Result.ok("演示数据已重置", demoService.reset());
    }

    /** 只重建基础数据，不动历史订单 */
    @PostMapping("/seed")
    public Result<Map<String, Object>> seed() {
        guard();
        return Result.ok("基础数据已生成", demoService.seedBase());
    }

    /** 生成跨 N 天的历史订单，用于报表页出曲线 */
    @PostMapping("/seed-orders")
    public Result<Map<String, Object>> seedOrders(@RequestParam(defaultValue = "30") int days,
                                                  @RequestParam(defaultValue = "300") int total) {
        guard();
        return Result.ok("历史订单已生成", demoService.seedOrders(days, total));
    }

    /** 看看现在库里有啥，方便确认数据灌进去了 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(demoService.status());
    }

    private void guard() {
        if (!enabled) {
            throw new BizException(ErrorCode.FORBIDDEN, "演示数据接口未开启（pos.demo.enabled=false）");
        }
    }
}
