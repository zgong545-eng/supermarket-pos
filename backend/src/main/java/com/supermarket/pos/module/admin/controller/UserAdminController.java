package com.supermarket.pos.module.admin.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M12 员工与角色管理接口。
 *
 * <p>权限点分两级：{@code sys:user} 看一眼，{@code sys:user:edit} 才能改。
 * 收银员只有前者，能看员工名单但不能动——这是刻意留的"越权演示点"，
 * 答辩时用一个收银员账号去调改人接口，会返回 403，效果比讲十分钟更直观。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    // ---------- 员工 ----------

    @GetMapping("/user/page")
    @PreAuthorize("hasAuthority('sys:user')")
    public Result<List<Map<String, Object>>> page(@RequestParam(required = false) String keyword) {
        return Result.ok(userAdminService.page(keyword));
    }

    @PostMapping("/user")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawRoles = (List<Object>) body.get("roleIds");
        List<Long> roleIds = rawRoles == null ? List.of()
                : rawRoles.stream().map(o -> ((Number) o).longValue()).toList();
        return Result.ok("员工已创建", userAdminService.create(
                str(body.get("username")),
                str(body.get("password")),
                str(body.get("realName")),
                str(body.get("phone")),
                str(body.get("email")),
                body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue(),
                roleIds));
    }

    @PutMapping("/user/{id}")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        userAdminService.update(id, str(body.get("realName")), str(body.get("phone")),
                str(body.get("email")),
                body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
        return Result.ok("已保存", null);
    }

    @PostMapping("/user/{id}/password")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        userAdminService.resetPassword(id, password);
        return Result.ok("密码已重置", null);
    }

    @PostMapping("/user/{id}/status")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        userAdminService.toggleStatus(id, status);
        return Result.ok(status == 1 ? "已启用" : "已停用", null);
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        userAdminService.delete(id);
        return Result.ok("已删除", null);
    }

    @PostMapping("/user/{id}/roles")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userAdminService.assignRoles(id, roleIds);
        return Result.ok("角色已更新", null);
    }

    // ---------- 角色与权限 ----------

    @GetMapping("/role/list")
    @PreAuthorize("hasAnyAuthority('sys:role','sys:user')")
    public Result<List<Map<String, Object>>> roles() {
        return Result.ok(userAdminService.roles());
    }

    @GetMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority('sys:role')")
    public Result<List<Long>> rolePermissions(@PathVariable Long id) {
        return Result.ok(userAdminService.rolePermissions(id));
    }

    @PostMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority('sys:role')")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        userAdminService.assignPermissions(id, permissionIds);
        return Result.ok("权限已更新（相关用户需重新登录生效）", null);
    }

    @GetMapping("/permission/tree")
    @PreAuthorize("hasAnyAuthority('sys:role','sys:user')")
    public Result<List<Map<String, Object>>> permissionTree() {
        return Result.ok(userAdminService.permissionTree());
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
