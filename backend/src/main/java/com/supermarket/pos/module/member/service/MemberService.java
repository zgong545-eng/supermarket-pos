package com.supermarket.pos.module.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.supermarket.pos.common.api.PageResult;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.member.domain.MemMember;
import com.supermarket.pos.module.member.mapper.MemMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * M04 会员服务。
 *
 * <p>积分和余额的扣减都是单条带条件 UPDATE（{@code AND points >= ?}），
 * 和库存一个道理：不能让两个收银台同时把同一笔积分花两次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemMemberMapper memberMapper;
    private final JdbcTemplate jdbc;

    // ============================================================
    //  查询
    // ============================================================

    public PageResult<Map<String, Object>> page(String keyword, int page, int size) {
        String like = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : "%";
        long offset = (long) (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.id, m.member_no AS memberNo, m.name, m.phone, m.points,
                       m.status, m.created_at AS createdAt, m.last_consume_at AS lastConsumeAt,
                       l.name AS levelName, l.discount_rate AS discountRate,
                       COALESCE(w.balance, 0) AS balance
                  FROM mem_member m
                  JOIN mem_level l ON l.id = m.level_id
                  LEFT JOIN mem_wallet w ON w.member_id = m.id
                 WHERE m.deleted = 0
                   AND (m.name LIKE ? OR m.phone LIKE ? OR m.member_no LIKE ?)
                 ORDER BY m.id DESC
                 LIMIT ? OFFSET ?
                """, like, like, like, size, offset);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mem_member m
                 WHERE m.deleted = 0 AND (m.name LIKE ? OR m.phone LIKE ? OR m.member_no LIKE ?)
                """, Long.class, like, like, like);
        PageResult<Map<String, Object>> r = new PageResult<>();
        r.setRecords(rows);
        r.setTotal(total == null ? 0 : total);
        r.setPage(page);
        r.setPageSize(size);
        return r;
    }

    /** 收银台的会员识别入口 */
    public Map<String, Object> lookup(String key) {
        if (!StringUtils.hasText(key)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请输入手机号或会员号");
        }
        Map<String, Object> m = memberMapper.selectByPhoneOrNo(key.trim());
        if (m == null) {
            throw BizException.of(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (((Number) m.get("status")).intValue() != 1) {
            throw BizException.of(ErrorCode.MEMBER_DISABLED);
        }
        // 顺带把可用券数带出来，收银员一眼能看到"这个顾客有券"
        Integer couponCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mem_member_coupon
                 WHERE member_id = ? AND status = 'UNUSED' AND expire_at > NOW()
                """, Integer.class, m.get("id"));
        m.put("unusedCouponCount", couponCount == null ? 0 : couponCount);
        return m;
    }

    public Map<String, Object> detail(Long memberId) {
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT m.*, l.name AS levelName, l.discount_rate AS discountRate,
                       l.point_rate AS pointRate, COALESCE(w.balance, 0) AS balance
                  FROM mem_member m
                  JOIN mem_level l ON l.id = m.level_id
                  LEFT JOIN mem_wallet w ON w.member_id = m.id
                 WHERE m.id = ? AND m.deleted = 0
                """, memberId);
        if (list.isEmpty()) {
            throw BizException.of(ErrorCode.MEMBER_NOT_FOUND);
        }
        return list.get(0);
    }

    public List<Map<String, Object>> pointsLog(Long memberId, int limit) {
        return jdbc.queryForList("""
                SELECT id, change_type AS changeType, points, before_points AS beforePoints,
                       after_points AS afterPoints, biz_no AS bizNo, remark, created_at AS createdAt
                  FROM mem_point_log WHERE member_id = ? ORDER BY id DESC LIMIT ?
                """, memberId, limit);
    }

    public List<Map<String, Object>> walletLog(Long memberId, int limit) {
        return jdbc.queryForList("""
                SELECT id, txn_type AS txnType, amount, before_balance AS beforeBalance,
                       after_balance AS afterBalance, biz_no AS bizNo, remark, created_at AS createdAt
                  FROM mem_wallet_txn WHERE member_id = ? ORDER BY id DESC LIMIT ?
                """, memberId, limit);
    }

    /** 会员持有的券（收银台选券用） */
    public List<Map<String, Object>> coupons(Long memberId, boolean onlyUsable) {
        String extra = onlyUsable ? " AND mc.status = 'UNUSED' AND mc.expire_at > NOW()" : "";
        return jdbc.queryForList("""
                SELECT mc.id, mc.coupon_id AS couponId, mc.status, mc.expire_at AS expireAt,
                       c.name, c.type, c.face_value AS faceValue, c.min_amount AS minAmount,
                       c.coupon_code AS couponCode
                  FROM mem_member_coupon mc
                  JOIN pmt_coupon c ON c.id = mc.coupon_id
                 WHERE mc.member_id = ?""" + extra + """
                 ORDER BY mc.expire_at ASC
                """, memberId);
    }

    // ============================================================
    //  开卡 / 充值 / 积分
    // ============================================================

    @Transactional
    public Long register(String name, String phone, Long levelId) {
        if (!StringUtils.hasText(phone) || !phone.matches("1\\d{10}")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        Long exists = memberMapper.selectCount(
                Wrappers.<MemMember>lambdaQuery().eq(MemMember::getPhone, phone));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.MEMBER_PHONE_DUPLICATED);
        }

        Long defaultLevel = levelId;
        if (defaultLevel == null) {
            defaultLevel = jdbc.queryForObject(
                    "SELECT id FROM mem_level WHERE code = 'NORMAL'", Long.class);
        }

        MemMember m = new MemMember();
        // member_no 是 NOT NULL 且唯一，但正式编号要等拿到自增 id 才能生成。
        // 先塞一个必定唯一的临时值占位，插完立刻改成 M+8位 id。
        m.setMemberNo("TMP" + phone);
        m.setName(name);
        m.setPhone(phone);
        m.setLevelId(defaultLevel);
        m.setPoints(0);
        m.setTotalPoints(0);
        m.setStatus(1);
        m.setGender(0);
        m.setRegisterStoreId(1L);
        memberMapper.insert(m);

        // 会员号用 id 派生，避免再引入一套序列
        String memberNo = "M" + String.format("%08d", m.getId());
        memberMapper.update(null, Wrappers.<MemMember>lambdaUpdate()
                .eq(MemMember::getId, m.getId())
                .set(MemMember::getMemberNo, memberNo));

        jdbc.update("INSERT INTO mem_wallet (member_id, balance, version, updated_at) VALUES (?,0,0,NOW())",
                m.getId());

        log.info("新会员开卡 {} {} ({})", m.getId(), name, phone);
        return m.getId();
    }

    /** 储值充值 */
    @Transactional
    public Long recharge(Long memberId, Long amount, String remark) {
        if (amount == null || amount <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "充值金额必须大于 0");
        }
        detail(memberId);   // 确认会员存在
        Long before = jdbc.queryForObject(
                "SELECT balance FROM mem_wallet WHERE member_id = ?", Long.class, memberId);
        if (before == null) {
            jdbc.update("INSERT INTO mem_wallet (member_id, balance, version, updated_at) VALUES (?,0,0,NOW())",
                    memberId);
            before = 0L;
        }
        jdbc.update("UPDATE mem_wallet SET balance = balance + ?, version = version + 1, updated_at = NOW() WHERE member_id = ?",
                amount, memberId);
        Long after = before + amount;
        jdbc.update("""
                INSERT INTO mem_wallet_txn (member_id, txn_type, amount, before_balance, after_balance, remark)
                VALUES (?, 'RECHARGE', ?, ?, ?, ?)
                """, memberId, amount, before, after, remark == null ? "会员充值" : remark);
        return after;
    }

    /** 储值扣款，被支付模块调用 */
    @Transactional
    public void walletPay(Long memberId, Long amount, String bizNo) {
        int rows = jdbc.update("""
                UPDATE mem_wallet SET balance = balance - ?, version = version + 1, updated_at = NOW()
                 WHERE member_id = ? AND balance >= ?
                """, amount, memberId, amount);
        if (rows == 0) {
            Long balance = jdbc.queryForObject(
                    "SELECT COALESCE(balance,0) FROM mem_wallet WHERE member_id = ?", Long.class, memberId);
            throw BizException.of(ErrorCode.WALLET_BALANCE_NOT_ENOUGH,
                    "储值余额不足，当前余额 " + (balance == null ? 0 : balance / 100.0) + " 元");
        }
        Long after = jdbc.queryForObject(
                "SELECT balance FROM mem_wallet WHERE member_id = ?", Long.class, memberId);
        jdbc.update("""
                INSERT INTO mem_wallet_txn (member_id, txn_type, amount, before_balance, after_balance, biz_no)
                VALUES (?, 'CONSUME', ?, ?, ?, ?)
                """, memberId, -amount, after + amount, after, bizNo);
    }

    /** 消费送积分 + 累计积分（累计积分决定等级，只增不减） */
    @Transactional
    public void earnPoints(Long memberId, int points, String bizNo) {
        if (points <= 0) {
            return;
        }
        jdbc.update("""
                UPDATE mem_member SET points = points + ?, total_points = total_points + ?, updated_at = NOW()
                 WHERE id = ?
                """, points, points, memberId);
        Map<String, Object> m = jdbc.queryForMap(
                "SELECT points FROM mem_member WHERE id = ?", memberId);
        int after = ((Number) m.get("points")).intValue();
        jdbc.update("""
                INSERT INTO mem_point_log (member_id, change_type, points, before_points, after_points, biz_no, remark)
                VALUES (?, 'EARN', ?, ?, ?, ?, '消费送积分')
                """, memberId, points, after - points, after, bizNo);
        upgradeLevelIfNeeded(memberId);
    }

    /** 积分抵扣，被订单模块调用 */
    @Transactional
    public void deductPoints(Long memberId, int points, String bizNo) {
        if (points <= 0) {
            return;
        }
        int rows = jdbc.update(
                "UPDATE mem_member SET points = points - ?, updated_at = NOW() WHERE id = ? AND points >= ?",
                points, memberId, points);
        if (rows == 0) {
            throw BizException.of(ErrorCode.POINT_NOT_ENOUGH);
        }
        Map<String, Object> m = jdbc.queryForMap("SELECT points FROM mem_member WHERE id = ?", memberId);
        int after = ((Number) m.get("points")).intValue();
        jdbc.update("""
                INSERT INTO mem_point_log (member_id, change_type, points, before_points, after_points, biz_no, remark)
                VALUES (?, 'USE', ?, ?, ?, ?, '积分抵扣')
                """, memberId, -points, after + points, after, bizNo);
    }

    /** 退货时把积分和余额退回 */
    @Transactional
    public void refundPoints(Long memberId, int points, String bizNo) {
        if (points <= 0) {
            return;
        }
        jdbc.update("UPDATE mem_member SET points = points + ?, updated_at = NOW() WHERE id = ?",
                points, memberId);
        Map<String, Object> m = jdbc.queryForMap("SELECT points FROM mem_member WHERE id = ?", memberId);
        int after = ((Number) m.get("points")).intValue();
        jdbc.update("""
                INSERT INTO mem_point_log (member_id, change_type, points, before_points, after_points, biz_no, remark)
                VALUES (?, 'ADJUST', ?, ?, ?, ?, '退货返还积分')
                """, memberId, points, after - points, after, bizNo);
    }

    @Transactional
    public void refundWallet(Long memberId, Long amount, String bizNo) {
        if (amount == null || amount <= 0) {
            return;
        }
        jdbc.update("UPDATE mem_wallet SET balance = balance + ?, version = version + 1, updated_at = NOW() WHERE member_id = ?",
                amount, memberId);
        Long after = jdbc.queryForObject(
                "SELECT balance FROM mem_wallet WHERE member_id = ?", Long.class, memberId);
        jdbc.update("""
                INSERT INTO mem_wallet_txn (member_id, txn_type, amount, before_balance, after_balance, biz_no)
                VALUES (?, 'REFUND', ?, ?, ?, ?)
                """, memberId, amount, after - amount, after, bizNo);
    }

    /** 等级自动升级：累计积分够了就往上升 */
    private void upgradeLevelIfNeeded(Long memberId) {
        Integer total = jdbc.queryForObject(
                "SELECT total_points FROM mem_member WHERE id = ?", Integer.class, memberId);
        if (total == null) {
            return;
        }
        Long newLevel = jdbc.queryForObject("""
                SELECT id FROM mem_level
                 WHERE status = 1 AND min_points <= ?
                 ORDER BY min_points DESC LIMIT 1
                """, Long.class, total);
        if (newLevel == null) {
            return;
        }
        jdbc.update("UPDATE mem_member SET level_id = ?, last_consume_at = NOW() WHERE id = ? AND level_id <> ?",
                newLevel, memberId, newLevel);
    }

    // ============================================================
    //  优惠券领取
    // ============================================================

    @Transactional
    public void issueCoupon(Long memberId, Long couponId) {
        detail(memberId);
        List<Map<String, Object>> tpl = jdbc.queryForList(
                "SELECT * FROM pmt_coupon WHERE id = ? AND status = 1", couponId);
        if (tpl.isEmpty()) {
            throw BizException.of(ErrorCode.COUPON_NOT_FOUND);
        }
        Map<String, Object> c = tpl.get(0);

        // 每人限领
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mem_member_coupon WHERE member_id = ? AND coupon_id = ?",
                Integer.class, memberId, couponId);
        int perLimit = ((Number) c.get("per_limit")).intValue();
        if (owned != null && owned >= perLimit) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED, "每人限领 " + perLimit + " 张");
        }

        // 发行量：单条 UPDATE 带条件，避免超发
        int totalQty = ((Number) c.get("total_qty")).intValue();
        if (totalQty > 0) {
            int rows = jdbc.update(
                    "UPDATE pmt_coupon SET issued_qty = issued_qty + 1 WHERE id = ? AND issued_qty < total_qty",
                    couponId);
            if (rows == 0) {
                throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED, "该券已被领完");
            }
        } else {
            jdbc.update("UPDATE pmt_coupon SET issued_qty = issued_qty + 1 WHERE id = ?", couponId);
        }

        int validDays = ((Number) c.get("valid_days")).intValue();
        LocalDateTime expireAt = validDays > 0
                ? LocalDateTime.now().plusDays(validDays)
                : ((java.sql.Timestamp) c.get("end_at")).toLocalDateTime();

        jdbc.update("""
                INSERT INTO mem_member_coupon (member_id, coupon_id, status, receive_at, expire_at)
                VALUES (?, ?, 'UNUSED', NOW(), ?)
                """, memberId, couponId, expireAt);
    }

    public List<Map<String, Object>> couponTemplates() {
        return jdbc.queryForList("""
                SELECT id, coupon_code AS couponCode, name, type, face_value AS faceValue,
                       min_amount AS minAmount, total_qty AS totalQty, issued_qty AS issuedQty,
                       per_limit AS perLimit, start_at AS startAt, end_at AS endAt, valid_days AS validDays
                  FROM pmt_coupon WHERE status = 1 ORDER BY id DESC
                """);
    }
}
