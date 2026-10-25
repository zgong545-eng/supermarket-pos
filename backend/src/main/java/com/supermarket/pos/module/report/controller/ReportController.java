package com.supermarket.pos.module.report.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M09 报表接口。
 *
 * <p>前端首页只调 {@code /overview} 一个接口，一次拿到 KPI + 趋势 + 支付占比 + 热销榜 + 分时。
 * 拆成五个接口会让首页发五个请求，答辩现场网速不好就转圈。
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 首页总览：一次拿全 */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.overview(days));
    }

    @GetMapping("/kpi")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> kpi(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.kpi(days));
    }

    @GetMapping("/series")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<List<Map<String, Object>>> series(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.series(days));
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<List<Map<String, Object>>> channels(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.channels(days));
    }

    @GetMapping("/top-sku")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<List<Map<String, Object>>> topSku(@RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(reportService.topSku(days, limit));
    }

    @GetMapping("/hourly")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<List<Map<String, Object>>> hourly() {
        return Result.ok(reportService.hourly());
    }

    @GetMapping("/member")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> member(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(reportService.memberContribution(days));
    }

    /** 手动刷新预聚合表（正常由定时任务做，演示时点一下刷数据） */
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<Map<String, Object>> rebuild(@RequestParam(defaultValue = "30") int days) {
        return Result.ok("预聚合已刷新", reportService.rebuild(days));
    }
}
