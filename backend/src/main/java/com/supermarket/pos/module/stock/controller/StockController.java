package com.supermarket.pos.module.stock.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M03 库存接口。
 *
 * <p>补货是这套系统里唯一"业务上能省、技术上不能省"的地方：
 * 入口简化为经理填数量，但入库仍然要写单据、改库存、留流水，三件事在一个事务里。
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/page")
    @PreAuthorize("hasAnyAuthority('stock:view','pos:checkout')")
    public Result<List<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stockService.pageStock(keyword, page, size));
    }

    @GetMapping("/{skuId}")
    @PreAuthorize("hasAuthority('stock:view')")
    public Result<Map<String, Object>> detail(@PathVariable Long skuId) {
        return Result.ok(stockService.stockDetail(skuId));
    }

    /** 库存预警：低于安全库存的商品，按缺口排序 */
    @GetMapping("/warning")
    @PreAuthorize("hasAnyAuthority('stock:view','stock:replenish')")
    public Result<List<Map<String, Object>>> warning(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(stockService.warningList(limit));
    }

    /** 补货入库：body 是明细数组，提交即生效 */
    @PostMapping("/replenish")
    @PreAuthorize("hasAuthority('stock:replenish')")
    public Result<String> replenish(@RequestBody List<StockService.BillItemForm> items,
                                    @RequestParam(required = false) String remark) {
        return Result.ok("补货已入库", stockService.replenish(items, remark));
    }

    @PostMapping("/loss")
    @PreAuthorize("hasAuthority('stock:loss')")
    public Result<String> loss(@RequestBody List<StockService.BillItemForm> items,
                               @RequestParam(required = false) String remark) {
        return Result.ok("报损已生效", stockService.loss(items, remark));
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('stock:check')")
    public Result<String> check(@RequestBody List<StockService.BillItemForm> items,
                                @RequestParam(required = false) String remark) {
        return Result.ok("盘点已生效", stockService.stockCheck(items, remark));
    }

    @GetMapping("/bill/page")
    @PreAuthorize("hasAuthority('stock:view')")
    public Result<List<Map<String, Object>>> bills(@RequestParam(required = false) String type,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stockService.billPage(type, page, size));
    }

    @GetMapping("/logs/{skuId}")
    @PreAuthorize("hasAuthority('stock:view')")
    public Result<List<Map<String, Object>>> logs(@PathVariable Long skuId,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(stockService.logs(skuId, limit));
    }

    /** 一致性自检：on_hand = available + locked 是否恒成立 */
    @GetMapping("/invariants")
    public Result<Map<String, Object>> invariants() {
        return Result.ok(stockService.invariants());
    }
}
