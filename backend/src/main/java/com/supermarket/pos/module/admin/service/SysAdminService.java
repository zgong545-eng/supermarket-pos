package com.supermarket.pos.module.admin.service;

import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M13 系统管理：字典、门店、参数、外设、日志。
 *
 * <p>这些东西的共同点是"改一次、到处用"，所以必须集中管理：
 * 字典决定下拉框有哪些选项，参数决定业务规则（积分抵扣上限、挂单超时等），
 * 门店决定库存和订单归属哪家店。
 *
 * <p><b>为什么不给"删除字典"开口子？</b>
 * 字典项被历史数据引用着（比如订单里存的是 {@code channel_code = 'CASH'}），
 * 删了字典项，历史订单就显示成一串英文码。所以只能停用，不能删。
 */
@Service
@RequiredArgsConstructor
public class SysAdminService {

    private final JdbcTemplate jdbc;

    // ============================================================
    //  字典
    // ============================================================

    public List<Map<String, Object>> dictTypes() {
        return jdbc.queryForList("""
                SELECT d.id, d.dict_type AS dictType, d.dict_name AS dictName, d.remark,
                       (SELECT COUNT(*) FROM sys_dict_item i WHERE i.dict_type = d.dict_type) AS itemCount
                  FROM sys_dict d ORDER BY d.id
                """);
    }

    public List<Map<String, Object>> dictItems(String dictType) {
        return jdbc.queryForList("""
                SELECT id, dict_type AS dictType, item_value AS itemValue, item_label AS itemLabel,
                       sort, status
                  FROM sys_dict_item WHERE dict_type = ? ORDER BY sort, id
                """, dictType);
    }

    /** 一次拿全部字典，前端启动时拉一遍就够，不用每个页面都请求 */
    public Map<String, List<Map<String, Object>>> allDicts() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dict_type AS dictType, item_value AS itemValue, item_label AS itemLabel, sort
                  FROM sys_dict_item WHERE status = 1 ORDER BY dict_type, sort, id
                """);
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("dictType");
            out.computeIfAbsent(type, k -> new java.util.ArrayList<>())
                    .add(Map.of("value", row.get("itemValue"), "label", row.get("itemLabel")));
        }
        return out;
    }

    @Transactional
    public void saveDictItem(String dictType, String itemValue, String itemLabel, int sort) {
        if (dictType == null || itemValue == null || itemLabel == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "字典类型、值、标签都不能为空");
        }
        jdbc.update("""
                INSERT INTO sys_dict_item (dict_type, item_value, item_label, sort, status)
                VALUES (?,?,?,?,1)
                ON DUPLICATE KEY UPDATE item_label = VALUES(item_label), sort = VALUES(sort)
                """, dictType, itemValue, itemLabel, sort);
    }

    @Transactional
    public void toggleDictItem(Long id, int status) {
        jdbc.update("UPDATE sys_dict_item SET status = ? WHERE id = ?", status, id);
    }

    // ============================================================
    //  门店
    // ============================================================

    public List<Map<String, Object>> stores() {
        return jdbc.queryForList("""
                SELECT id, code, name, address, phone, business_hours AS businessHours, status
                  FROM sys_store ORDER BY id
                """);
    }

    @Transactional
    public void saveStore(Long id, String code, String name, String address,
                          String phone, String businessHours) {
        if (code == null || name == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "门店编码和名称不能为空");
        }
        if (id == null) {
            jdbc.update("""
                    INSERT INTO sys_store (code, name, address, phone, business_hours, status)
                    VALUES (?,?,?,?,?,1)
                    """, code, name, address, phone, businessHours);
        } else {
            jdbc.update("""
                    UPDATE sys_store SET code = ?, name = ?, address = ?, phone = ?,
                           business_hours = ?, updated_at = NOW()
                     WHERE id = ?
                    """, code, name, address, phone, businessHours, id);
        }
    }

    // ============================================================
    //  系统参数
    // ============================================================

    public List<Map<String, Object>> configs() {
        return jdbc.queryForList("""
                SELECT id, config_key AS configKey, config_value AS configValue, remark, updated_at AS updatedAt
                  FROM sys_config ORDER BY config_key
                """);
    }

    @Transactional
    public void saveConfig(String key, String value) {
        if (key == null || key.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "参数名不能为空");
        }
        int n = jdbc.update(
                "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
                value == null ? "" : value, key);
        if (n == 0) {
            jdbc.update("INSERT INTO sys_config (config_key, config_value) VALUES (?,?)", key, value);
        }
    }

    // ============================================================
    //  外设
    // ============================================================

    public List<Map<String, Object>> devices() {
        return jdbc.queryForList("""
                SELECT d.id, d.code, d.name, d.type, d.store_id AS storeId, d.status, d.remark,
                       s.name AS storeName
                  FROM sys_device d LEFT JOIN sys_store s ON s.id = d.store_id
                 ORDER BY d.id
                """);
    }

    @Transactional
    public void saveDevice(Long id, String code, String name, String type, Long storeId, String remark) {
        if (code == null || name == null || type == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "设备编号、名称、类型都不能为空");
        }
        if (id == null) {
            jdbc.update("""
                    INSERT INTO sys_device (code, name, type, store_id, status, remark)
                    VALUES (?,?,?,?,1,?)
                    """, code, name, type, storeId, remark);
        } else {
            jdbc.update("""
                    UPDATE sys_device SET code = ?, name = ?, type = ?, store_id = ?, remark = ?
                     WHERE id = ?
                    """, code, name, type, storeId, remark, id);
        }
    }

    @Transactional
    public void toggleDevice(Long id, int status) {
        jdbc.update("UPDATE sys_device SET status = ? WHERE id = ?", status, id);
    }

    // ============================================================
    //  日志
    // ============================================================

    public List<Map<String, Object>> operLogs(String keyword, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        String base = """
                SELECT id, username, module, action, method, uri, ip,
                       cost_ms AS costMs, success, error_msg AS errorMsg, created_at AS createdAt
                  FROM sys_oper_log
                """;
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            return jdbc.queryForList(base
                            + " WHERE username LIKE ? OR action LIKE ? OR uri LIKE ?"
                            + " ORDER BY id DESC LIMIT ? OFFSET ?",
                    like, like, like, size, offset);
        }
        return jdbc.queryForList(base + " ORDER BY id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public List<Map<String, Object>> loginLogs(int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return jdbc.queryForList("""
                SELECT id, username, ip, ua, success, msg, created_at AS createdAt
                  FROM sys_login_log ORDER BY id DESC LIMIT ? OFFSET ?
                """, size, offset);
    }

    /** 首页小卡片：今日订单/今日销售额/库存预警数/待处理事项 */
    public Map<String, Object> dashboard() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("todayOrders", jdbc.queryForObject(
                "SELECT COUNT(*) FROM so_order WHERE DATE(created_at) = CURDATE()", Integer.class));
        r.put("todayAmount", jdbc.queryForObject("""
                SELECT COALESCE(SUM(payable_amount),0) FROM so_order
                 WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','COMPLETED')
                """, Long.class));
        r.put("warningCount", jdbc.queryForObject("""
                SELECT COUNT(*) FROM inv_stock WHERE available <= safety_stock
                """, Integer.class));
        r.put("memberCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM mem_member WHERE status = 1", Integer.class));
        r.put("skuCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM pms_sku WHERE status = 1 AND deleted = 0", Integer.class));
        r.put("openShift", jdbc.queryForObject("""
                SELECT COUNT(*) FROM pos_shift WHERE status = 'OPEN'
                """, Integer.class));
        return r;
    }
}
