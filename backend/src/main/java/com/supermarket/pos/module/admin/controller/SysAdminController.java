package com.supermarket.pos.module.admin.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.admin.service.SysAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M13 系统管理接口：字典 / 门店 / 参数 / 外设 / 日志 / 首页卡片。
 *
 * <p>{@code /api/admin/dict/all} 是唯一放开给所有登录用户的接口——
 * 前端每个下拉框都要字典，不能每人都要求 {@code sys:dict} 权限。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SysAdminController {

    private final SysAdminService sysAdminService;

    // ---------- 首页 ----------

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(sysAdminService.dashboard());
    }

    // ---------- 字典 ----------

    @GetMapping("/dict/all")
    public Result<Map<String, List<Map<String, Object>>>> allDicts() {
        return Result.ok(sysAdminService.allDicts());
    }

    @GetMapping("/dict/types")
    @PreAuthorize("hasAuthority('sys:dict')")
    public Result<List<Map<String, Object>>> dictTypes() {
        return Result.ok(sysAdminService.dictTypes());
    }

    @GetMapping("/dict/items")
    @PreAuthorize("hasAuthority('sys:dict')")
    public Result<List<Map<String, Object>>> dictItems(@RequestParam String dictType) {
        return Result.ok(sysAdminService.dictItems(dictType));
    }

    @PostMapping("/dict/item")
    @PreAuthorize("hasAuthority('sys:dict')")
    public Result<Void> saveDictItem(@RequestBody Map<String, Object> body) {
        sysAdminService.saveDictItem(
                (String) body.get("dictType"),
                (String) body.get("itemValue"),
                (String) body.get("itemLabel"),
                body.get("sort") == null ? 0 : ((Number) body.get("sort")).intValue());
        return Result.ok("字典项已保存", null);
    }

    @PostMapping("/dict/item/{id}/status")
    @PreAuthorize("hasAuthority('sys:dict')")
    public Result<Void> toggleDictItem(@PathVariable Long id, @RequestParam int status) {
        sysAdminService.toggleDictItem(id, status);
        return Result.ok(status == 1 ? "已启用" : "已停用", null);
    }

    // ---------- 门店 ----------

    @GetMapping("/store/list")
    @PreAuthorize("hasAnyAuthority('sys:store','sys:user')")
    public Result<List<Map<String, Object>>> stores() {
        return Result.ok(sysAdminService.stores());
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('sys:store')")
    public Result<Void> saveStore(@RequestBody Map<String, Object> body) {
        sysAdminService.saveStore(
                body.get("id") == null ? null : ((Number) body.get("id")).longValue(),
                (String) body.get("code"),
                (String) body.get("name"),
                (String) body.get("address"),
                (String) body.get("phone"),
                (String) body.get("businessHours"));
        return Result.ok("门店已保存", null);
    }

    // ---------- 参数 ----------

    @GetMapping("/config/list")
    @PreAuthorize("hasAuthority('sys:config')")
    public Result<List<Map<String, Object>>> configs() {
        return Result.ok(sysAdminService.configs());
    }

    @PostMapping("/config")
    @PreAuthorize("hasAuthority('sys:config')")
    public Result<Void> saveConfig(@RequestParam String key, @RequestParam String value) {
        sysAdminService.saveConfig(key, value);
        return Result.ok("参数已保存", null);
    }

    // ---------- 外设 ----------

    @GetMapping("/device/list")
    @PreAuthorize("hasAuthority('sys:device')")
    public Result<List<Map<String, Object>>> devices() {
        return Result.ok(sysAdminService.devices());
    }

    @PostMapping("/device")
    @PreAuthorize("hasAuthority('sys:device')")
    public Result<Void> saveDevice(@RequestBody Map<String, Object> body) {
        sysAdminService.saveDevice(
                body.get("id") == null ? null : ((Number) body.get("id")).longValue(),
                (String) body.get("code"),
                (String) body.get("name"),
                (String) body.get("type"),
                body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue(),
                (String) body.get("remark"));
        return Result.ok("设备已保存", null);
    }

    @PostMapping("/device/{id}/status")
    @PreAuthorize("hasAuthority('sys:device')")
    public Result<Void> toggleDevice(@PathVariable Long id, @RequestParam int status) {
        sysAdminService.toggleDevice(id, status);
        return Result.ok(status == 1 ? "已上线" : "已下线", null);
    }

    // ---------- 日志 ----------

    @GetMapping("/log/oper")
    @PreAuthorize("hasAuthority('sys:log')")
    public Result<List<Map<String, Object>>> operLogs(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(sysAdminService.operLogs(keyword, page, size));
    }

    @GetMapping("/log/login")
    @PreAuthorize("hasAuthority('sys:log')")
    public Result<List<Map<String, Object>>> loginLogs(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(sysAdminService.loginLogs(page, size));
    }
}
