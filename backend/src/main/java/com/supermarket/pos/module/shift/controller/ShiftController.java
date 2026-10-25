package com.supermarket.pos.module.shift.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.shift.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M08 交班接口。
 *
 * <p>正常流程：上班先 {@code /open}（录备用金）→ 卖货 → 下班 {@code /close}（录实点现金）。
 * 如果用了没开班就开单，{@code /current} 会返回一个自动创建的班次（备用金 0），
 * 避免交班页一片空白。
 */
@RestController
@RequestMapping("/api/shift")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    /** 开班，body: { openingCash: 20000, deviceCode: "POS-01" } */
    @PostMapping("/open")
    @PreAuthorize("hasAuthority('pos:shift')")
    public Result<Map<String, Object>> open(@RequestParam long openingCash,
                                            @RequestParam(required = false) String deviceCode,
                                            @RequestParam(required = false) String remark) {
        return Result.ok("开班成功", shiftService.open(openingCash, deviceCode, remark));
    }

    /** 交班，body: { closingCash: 150000 } */
    @PostMapping("/close")
    @PreAuthorize("hasAuthority('pos:shift')")
    public Result<Map<String, Object>> close(@RequestParam long closingCash,
                                             @RequestParam(required = false) String remark) {
        return Result.ok("交班完成", shiftService.close(closingCash, remark));
    }

    /** 当前班次（没开班返回 data=null，前端据此显示"请先开班"） */
    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('pos:shift','pos:checkout')")
    public Result<Map<String, Object>> current() {
        return Result.ok(shiftService.current());
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('pos:shift')")
    public Result<List<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(shiftService.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pos:shift')")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(shiftService.summary(id));
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAuthority('pos:shift')")
    public Result<List<Map<String, Object>>> orders(@PathVariable Long id) {
        return Result.ok(shiftService.orders(id));
    }
}
