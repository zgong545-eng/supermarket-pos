package com.supermarket.pos.common.sequence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单号生成器。
 *
 * <p>格式：{@code 前缀 + yyyyMMdd + 5 位流水}，例如 {@code SO2026102500012}。
 *
 * <p><b>为什么不用 UUID / 雪花 ID：</b>收银小票上要印单号，
 * <code>SO2026102500012</code> 收银员一眼看出是哪天第几单，对账直接按单号找；
 * UUID 做不到这一点。
 *
 * <p><b>为什么不用 {@code LAST_INSERT_ID(expr)} 那个常见技巧：</b>
 * 我试过，会出问题。{@code sys_sequence} 表有自增主键，
 * INSERT 分支执行完 MySQL 会把 {@code LAST_INSERT_ID()} 覆盖成新插入行的 id，
 * 于是第一次拿到的不是 1 而是主键值，下一次自增就跟它对不上，最终生成重复单号。
 * 这个坑在"第一次插入"和"后续更新"两条路径上表现不一致，非常难查。
 *
 * <p>现在改成三步：{@code INSERT IGNORE} 保证行存在 →
 * {@code SELECT ... FOR UPDATE} 拿行锁并读当前值 → 写回 +1。
 * 行锁保证并发下每个调用者拿到不同的数，逻辑一眼能看懂。
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbc;

    /**
     * @param prefix 业务前缀：SO 订单 / PY 支付 / RF 退款 / IN 补货 / LS 报损 / CK 盘点 / SH 交班 / HD 挂单
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String next(String prefix) {
        String day = LocalDate.now().format(DAY);

        // 1. 保证这一天的这一种前缀有一行（已存在则什么都不做）
        jdbc.update("""
                INSERT IGNORE INTO sys_sequence (seq_key, seq_date, current_value)
                VALUES (?, ?, 0)
                """, prefix, day);

        // 2. 锁住这行再读，避免并发读到同一个值
        Long current = jdbc.queryForObject("""
                SELECT current_value FROM sys_sequence
                 WHERE seq_key = ? AND seq_date = ?
                 FOR UPDATE
                """, Long.class, prefix, day);

        long next = (current == null ? 0L : current) + 1;

        // 3. 写回
        jdbc.update("""
                UPDATE sys_sequence SET current_value = ?, updated_at = NOW()
                 WHERE seq_key = ? AND seq_date = ?
                """, next, prefix, day);

        return prefix + day + String.format("%05d", next);
    }
}
