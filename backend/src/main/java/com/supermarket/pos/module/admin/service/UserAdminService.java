package com.supermarket.pos.module.admin.service;

import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M12 员工（系统用户）管理。
 *
 * <p>课堂演示里"招人"这件事没有 HR 系统对接，就是经理在这里直接建账号。
 * 建完立刻能登录，不需要审批流——这是入口简化，不影响权限模型的完整性。
 *
 * <p><b>三条不能省的安全规则</b>：
 * <ol>
 *   <li>密码只存 BCrypt，任何接口都不回显密码字段</li>
 *   <li>不能停用/删除自己（否则经理把自己锁在门外，只能改库救）</li>
 *   <li>不能删除最后一个 ADMIN，同理</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    /** 员工列表（带角色名） */
    public List<Map<String, Object>> page(String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email,
                       u.store_id AS storeId, u.status, u.last_login_at AS lastLoginAt,
                       u.created_at AS createdAt,
                       (SELECT GROUP_CONCAT(r.name ORDER BY r.id)
                          FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                         WHERE ur.user_id = u.id) AS roleNames,
                       (SELECT GROUP_CONCAT(r.code ORDER BY r.id)
                          FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                         WHERE ur.user_id = u.id) AS roleCodes
                  FROM sys_user u
                 WHERE u.deleted = 0
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.username LIKE ? OR u.real_name LIKE ? OR u.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 新建员工 */
    @Transactional
    public Map<String, Object> create(String username, String password, String realName,
                                      String phone, String email, Long storeId, List<Long> roleIds) {
        if (username == null || !username.matches("[a-zA-Z0-9_]{3,20}")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "登录名只能是 3-20 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }
        if (realName == null || realName.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "姓名不能为空");
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0",
                Integer.class, username);
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.DATA_CONFLICT, "登录名已被占用");
        }

        jdbc.update("""
                INSERT INTO sys_user (username, password, real_name, phone, email, store_id, status)
                VALUES (?,?,?,?,?,?,1)
                """, username, passwordEncoder.encode(password), realName, phone, email,
                storeId == null ? 1L : storeId);
        Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        bindRoles(userId, roleIds);
        return Map.of("id", userId, "username", username);
    }

    /** 改资料（不含密码） */
    @Transactional
    public void update(Long id, String realName, String phone, String email, Long storeId) {
        mustExist(id);
        jdbc.update("""
                UPDATE sys_user SET real_name = COALESCE(?, real_name), phone = ?,
                       email = ?, store_id = COALESCE(?, store_id), updated_at = NOW()
                 WHERE id = ?
                """, realName, phone, email, storeId, id);
    }

    /** 改密码：管理员重置，不需要旧密码 */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        mustExist(id);
        if (newPassword == null || newPassword.length() < 6) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }
        jdbc.update("UPDATE sys_user SET password = ?, updated_at = NOW() WHERE id = ?",
                passwordEncoder.encode(newPassword), id);
    }

    /** 启用/停用 */
    @Transactional
    public void toggleStatus(Long id, int status) {
        Long me = UserContext.currentUserId();
        if (id.equals(me) && status == 0) {
            throw BizException.of(ErrorCode.FORBIDDEN, "不能停用自己的账号");
        }
        mustExist(id);
        if (status == 0 && isLastActiveAdmin(id)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这是最后一个可用管理员，不能停用");
        }
        jdbc.update("UPDATE sys_user SET status = ?, updated_at = NOW() WHERE id = ?", status, id);
    }

    /** 逻辑删除 */
    @Transactional
    public void delete(Long id) {
        Long me = UserContext.currentUserId();
        if (id.equals(me)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "不能删除自己的账号");
        }
        mustExist(id);
        if (isLastActiveAdmin(id)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这是最后一个可用管理员，不能删除");
        }
        jdbc.update("UPDATE sys_user SET deleted = 1, updated_at = NOW() WHERE id = ?", id);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
    }

    /** 重新分配角色：先清后加，避免残留 */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        mustExist(userId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        bindRoles(userId, roleIds);
    }

    // ============================================================
    //  内部
    // ============================================================

    private void bindRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            jdbc.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?,?)", userId, roleId);
        }
    }

    private void mustExist(Long id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0", Integer.class, id);
        if (n == null || n == 0) {
            throw BizException.of(ErrorCode.NOT_FOUND, "员工不存在");
        }
    }

    /** 判断某人是不是"最后一个还能用的管理员" */
    private boolean isLastActiveAdmin(Long userId) {
        Integer isAdmin = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND r.code = 'ADMIN'
                """, Integer.class, userId);
        if (isAdmin == null || isAdmin == 0) {
            return false;
        }
        Integer others = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.id) FROM sys_user u
                  JOIN sys_user_role ur ON ur.user_id = u.id
                  JOIN sys_role r ON r.id = ur.role_id
                 WHERE r.code = 'ADMIN' AND u.status = 1 AND u.deleted = 0 AND u.id <> ?
                """, Integer.class, userId);
        return others == null || others == 0;
    }

    /** 角色下拉框 */
    public List<Map<String, Object>> roles() {
        return jdbc.queryForList("""
                SELECT r.id, r.code, r.name, r.remark, r.is_system AS isSystem,
                       (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id = r.id) AS userCount,
                       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS permCount
                  FROM sys_role r ORDER BY r.id
                """);
    }

    /** 角色已分配的权限 id 列表 */
    public List<Long> rolePermissions(Long roleId) {
        return jdbc.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ? ORDER BY permission_id",
                Long.class, roleId);
    }

    /** 给角色改权限：内置 ADMIN 不允许改（改了可能把自己关在外面） */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        List<Map<String, Object>> role = jdbc.queryForList(
                "SELECT code, is_system FROM sys_role WHERE id = ?", roleId);
        if (role.isEmpty()) {
            throw BizException.of(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if ("ADMIN".equals(role.get(0).get("code"))) {
            throw BizException.of(ErrorCode.FORBIDDEN, "内置管理员角色不允许改权限");
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                jdbc.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?,?)",
                        roleId, pid);
            }
        }
    }

    /** 权限树（管理端分配权限用） */
    public List<Map<String, Object>> permissionTree() {
        List<Map<String, Object>> all = jdbc.queryForList("""
                SELECT id, code, name, type, parent_id AS parentId, path, sort
                  FROM sys_permission ORDER BY sort, id
                """);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> p : all) {
            Map<String, Object> node = new LinkedHashMap<>(p);
            node.put("children", new ArrayList<Map<String, Object>>());
            byId.put(((Number) p.get("id")).longValue(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : byId.values()) {
            long parentId = ((Number) node.get("parentId")).longValue();
            if (parentId == 0 || !byId.containsKey(parentId)) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> siblings =
                        (List<Map<String, Object>>) byId.get(parentId).get("children");
                siblings.add(node);
            }
        }
        return roots;
    }
}
