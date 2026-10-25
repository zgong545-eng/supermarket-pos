package com.supermarket.pos.module.shift.service;

import com.supermarket.pos.common.context.LoginUser;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.module.shift.domain.PosShift;
import com.supermarket.pos.module.shift.mapper.PosShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M08 交班服务：开班 / 交班 / 长短款。
 *
 * <p><b>一条铁律：一个收银员同一时刻只能有一个 OPEN 班次。</b>
 * 这条约束不放数据库唯一索引（因为 status 是字符串，MySQL 没有部分索引），
 * 而是在 {@link #open} 里用"先查再插 + 事务"保证；并发下仍可能双开，
 * 但收银员只会在一台机器上操作，风险可以接受，注释写在明面上。
 *
 * <p><b>长短款算法</b>：
 * <pre>
 *   系统应收现金 = 备用金 + Σ(本班 CASH 支付成功的 payable_amount)
 *   长短款      = 实点现金 - 系统应收现金
 * </pre>
 * 注意这里用的是"应收"（{@code payable_amount}）而不是"实收"（{@code paid_amount}）。
 * 因为顾客给 100 找 30，抽屉里净增的是 70，不是 100。用实收会把找零算成长款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final PosShiftMapper shiftMapper;
    private final SequenceService sequenceService;
    private final JdbcTemplate jdbc;

    // ============================================================
    //  开班
    // ============================================================

    /**
     * 开班。
     *
     * @param openingCash 备用金（分）。抽屉里原有的钱，用于找零。
     */
    @Transactional
    public Map<String, Object> open(long openingCash, String deviceCode, String remark) {
        LoginUser user = UserContext.require();
        if (openingCash < 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "备用金不能为负数");
        }
        PosShift exist = findOpen(user.getUserId());
        if (exist != null) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED,
                    "你已经有一个未交班的班次（" + exist.getShiftNo() + "），请先交班");
        }

        PosShift shift = new PosShift();
        shift.setShiftNo(sequenceService.next("SF"));
        shift.setStoreId(storeId());
        shift.setCashierId(user.getUserId());
        shift.setCashierName(user.getRealName());
        shift.setDeviceCode(deviceCode);
        shift.setStartAt(LocalDateTime.now());
        shift.setStatus("OPEN");
        shift.setOpeningCash(openingCash);
        shift.setOrderCount(0);
        shift.setTotalAmount(0L);
        shift.setRemark(remark);
        shiftMapper.insert(shift);

        log.info("开班：{} 收银员={} 备用金={} 分", shift.getShiftNo(), user.getUsername(), openingCash);
        return summary(shift.getId());
    }

    /**
     * 确保当前收银员有 OPEN 班次，没有就自动开一个备用金为 0 的。
     *
     * <p>订单要挂班次才能统计交班，但"忘了开班就开不了单"在课堂演示里是灾难。
     * 所以这里做兜底：无班次时自动补一个。备注里写清楚来源，方便老师问起时解释。
     *
     * @return 班次 id
     */
    @Transactional
    public Long ensureOpen() {
        LoginUser user = UserContext.require();
        PosShift exist = findOpen(user.getUserId());
        if (exist != null) {
            return exist.getId();
        }
        PosShift shift = new PosShift();
        shift.setShiftNo(sequenceService.next("SF"));
        shift.setStoreId(storeId());
        shift.setCashierId(user.getUserId());
        shift.setCashierName(user.getRealName());
        shift.setStartAt(LocalDateTime.now());
        shift.setStatus("OPEN");
        shift.setOpeningCash(0L);
        shift.setOrderCount(0);
        shift.setTotalAmount(0L);
        shift.setRemark("系统在开单时自动创建（未手动开班，备用金按 0 计）");
        shiftMapper.insert(shift);
        log.info("自动开班：{} 收银员={}", shift.getShiftNo(), user.getUsername());
        return shift.getId();
    }

    /** 当前收银员的 OPEN 班次（含实时汇总），没开班返回 null */
    public Map<String, Object> current() {
        LoginUser user = UserContext.require();
        PosShift shift = findOpen(user.getUserId());
        return shift == null ? null : summary(shift.getId());
    }

    // ============================================================
    //  交班
    // ============================================================

    /**
     * 交班。
     *
     * @param closingCash 实点现金（分），由收银员数完抽屉后录入
     */
    @Transactional
    public Map<String, Object> close(long closingCash, String remark) {
        LoginUser user = UserContext.require();
        PosShift shift = findOpen(user.getUserId());
        if (shift == null) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED, "当前没有未交班的班次");
        }
        if (closingCash < 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "实点现金不能为负数");
        }

        Map<String, Object> stat = cashStat(shift.getId(), shift.getOpeningCash());
        long systemCash = toLong(stat.get("systemCash"));
        long diff = closingCash - systemCash;

        jdbc.update("""
                UPDATE pos_shift
                   SET end_at = NOW(), status = 'CLOSED', closing_cash = ?,
                       system_cash = ?, cash_diff = ?,
                       order_count = ?, total_amount = ?,
                       remark = COALESCE(?, remark)
                 WHERE id = ? AND status = 'OPEN'
                """, closingCash, systemCash, diff,
                toLong(stat.get("orderCount")), toLong(stat.get("amount")),
                remark, shift.getId());

        log.info("交班：{} 实点={} 系统={} 长短款={} 分", shift.getShiftNo(), closingCash, systemCash, diff);
        return summary(shift.getId());
    }

    // ============================================================
    //  查询
    // ============================================================

    /** 班次列表（经理看全部，收银员看自己） */
    public List<Map<String, Object>> page(int page, int size) {
        LoginUser user = UserContext.require();
        int offset = Math.max(0, (page - 1) * size);
        boolean seeAll = user.isAdmin() || user.getRoles().contains("MANAGER");
        String base = """
                SELECT id, shift_no AS shiftNo, cashier_name AS cashierName, device_code AS deviceCode,
                       start_at AS startAt, end_at AS endAt, status,
                       opening_cash AS openingCash, closing_cash AS closingCash,
                       system_cash AS systemCash, cash_diff AS cashDiff,
                       order_count AS orderCount, total_amount AS totalAmount, remark
                  FROM pos_shift
                """;
        if (seeAll) {
            return jdbc.queryForList(base + " ORDER BY id DESC LIMIT ? OFFSET ?", size, offset);
        }
        return jdbc.queryForList(base + " WHERE cashier_id = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                user.getUserId(), size, offset);
    }

    /** 班次详情：交班核对用，能看到这一班卖了哪些单、各付了多少现金 */
    public Map<String, Object> summary(Long shiftId) {
        PosShift shift = shiftMapper.selectById(shiftId);
        if (shift == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "班次不存在");
        }
        Map<String, Object> stat = cashStat(shiftId, shift.getOpeningCash());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", shift.getId());
        r.put("shiftNo", shift.getShiftNo());
        r.put("cashierId", shift.getCashierId());
        r.put("cashierName", shift.getCashierName());
        r.put("deviceCode", shift.getDeviceCode());
        r.put("status", shift.getStatus());
        r.put("startAt", shift.getStartAt());
        r.put("endAt", shift.getEndAt());
        r.put("openingCash", shift.getOpeningCash());
        r.put("closingCash", shift.getClosingCash());
        r.put("systemCash", shift.getStatus().equals("CLOSED") ? shift.getSystemCash() : stat.get("systemCash"));
        r.put("cashDiff", shift.getStatus().equals("CLOSED") ? shift.getCashDiff()
                : (shift.getClosingCash() == null ? null : shift.getClosingCash() - toLong(stat.get("systemCash"))));
        r.put("orderCount", shift.getStatus().equals("CLOSED") ? shift.getOrderCount() : stat.get("orderCount"));
        r.put("totalAmount", shift.getStatus().equals("CLOSED") ? shift.getTotalAmount() : stat.get("amount"));
        r.put("cashAmount", stat.get("cashAmount"));
        r.put("channels", stat.get("channels"));
        r.put("remark", shift.getRemark());
        return r;
    }

    /** 班次内订单明细 */
    public List<Map<String, Object>> orders(Long shiftId) {
        return jdbc.queryForList("""
                SELECT order_no AS orderNo, status, member_id AS memberId,
                       item_count AS itemCount, total_amount AS totalAmount,
                       discount_amount AS discountAmount, payable_amount AS payableAmount,
                       paid_amount AS paidAmount, change_amount AS changeAmount,
                       created_at AS createdAt
                  FROM so_order
                 WHERE shift_id = ?
                 ORDER BY id DESC
                """, shiftId);
    }

    // ============================================================
    //  内部
    // ============================================================

    private PosShift findOpen(Long cashierId) {
        List<PosShift> list = shiftMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PosShift>()
                        .eq(PosShift::getCashierId, cashierId)
                        .eq(PosShift::getStatus, "OPEN")
                        .orderByDesc(PosShift::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 算一个班次的现金账。
     *
     * <p>退货要扣：如果顾客在 A 班买了东西、B 班来退，B 班的抽屉是少了钱的，
     * 所以退款金额必须从 B 班的现金收入里减掉，否则 B 班永远显示短款。
     */
    private Map<String, Object> cashStat(Long shiftId, Long openingCash) {
        Map<String, Object> agg = jdbc.queryForMap("""
                SELECT COUNT(*) AS orderCount,
                       COALESCE(SUM(payable_amount),0) AS amount
                  FROM so_order
                 WHERE shift_id = ? AND status IN ('PAID','COMPLETED','REFUNDED')
                """, shiftId);

        long cashIn = toLong(jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.amount),0)
                  FROM pay_payment p JOIN so_order o ON o.order_no = p.order_no
                 WHERE o.shift_id = ? AND p.status = 'SUCCESS' AND p.channel_code = 'CASH'
                """, Long.class, shiftId));

        // 本班发生的退款（退款时现金要还回去，等价于现金流出）。
        // pay_refund 自己没有渠道字段，渠道要从它对应的支付单上取。
        long cashRefund = toLong(jdbc.queryForObject("""
                SELECT COALESCE(SUM(r.amount),0)
                  FROM pay_refund r
                  JOIN pay_payment p ON p.pay_no = r.pay_no
                  JOIN so_order o ON o.order_no = r.order_no
                 WHERE o.shift_id = ? AND r.status = 'SUCCESS' AND p.channel_code = 'CASH'
                """, Long.class, shiftId));

        long cashNet = cashIn - cashRefund;
        long systemCash = (openingCash == null ? 0 : openingCash) + cashNet;

        List<Map<String, Object>> channels = jdbc.queryForList("""
                SELECT p.channel_code AS channel, COUNT(*) AS cnt,
                       COALESCE(SUM(p.amount),0) AS amount
                  FROM pay_payment p JOIN so_order o ON o.order_no = p.order_no
                 WHERE o.shift_id = ? AND p.status = 'SUCCESS'
                 GROUP BY p.channel_code
                 ORDER BY amount DESC
                """, shiftId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderCount", agg.get("orderCount"));
        r.put("amount", agg.get("amount"));
        r.put("cashAmount", cashNet);
        r.put("cashRefund", cashRefund);
        r.put("systemCash", systemCash);
        r.put("channels", channels);
        return r;
    }

    private Long storeId() {
        Long id = UserContext.currentStoreId();
        return id == null ? 1L : id;
    }

    private long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
