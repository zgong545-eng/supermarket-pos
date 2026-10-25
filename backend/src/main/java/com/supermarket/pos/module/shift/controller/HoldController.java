package com.supermarket.pos.module.shift.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.shift.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M08 挂单接口。挂单不占库存，取单时重新算钱。 */
@RestController
@RequestMapping("/api/hold")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    /** 挂起：直接把收银台的购物车对象丢过来即可 */
    @PostMapping
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Map<String, Object>> hold(@RequestBody CartRequest req) {
        return Result.ok("已挂单", holdService.hold(req));
    }

    /** 取单：返回购物车快照 */
    @PostMapping("/{holdNo}/resume")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Map<String, Object>> resume(@PathVariable String holdNo) {
        return Result.ok("已取单", holdService.resume(holdNo));
    }

    @PostMapping("/{holdNo}/cancel")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Void> cancel(@PathVariable String holdNo) {
        holdService.cancel(holdNo);
        return Result.ok("挂单已作废", null);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(holdService.list());
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('pos:checkout')")
    public Result<Integer> count() {
        return Result.ok(holdService.count());
    }
}
