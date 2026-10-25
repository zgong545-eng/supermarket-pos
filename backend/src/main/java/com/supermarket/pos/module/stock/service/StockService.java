package com.supermarket.pos.module.stock.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.module.stock.domain.InvStock;
import com.supermarket.pos.module.stock.domain.StockBill;
import com.supermarket.pos.module.stock.domain.StockBillItem;
import com.supermarket.pos.module.stock.mapper.InvStockMapper;
import com.supermarket.pos.module.stock.mapper.StockBillItemMapper;
import com.supermarket.pos.module.stock.mapper.StockBillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * M03 库存服务。
 *
 * <p>这个模块的设计含量只有一句话：<b>所有数量变动都走单条带条件的 UPDATE</b>。
 * 业务层不出现 "先查库存再判断够不够再扣" 这种写法。
 *
 * <p>补货是本系统的"入口简化"典型：不接采购系统、不走审批，
 * 经理在界面上填数量 → 提交即入库。但入库这件事本身必须严谨：
 * 事务内写单据 + 改库存 + 写流水，三件事要么都成要么都不成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final InvStockMapper stockMapper;
    private final StockBillMapper billMapper;
    private final StockBillItemMapper billItemMapper;
    private final SequenceService sequenceService;
    private final JdbcTemplate jdbc;

    // ============================================================
    //  给其他模块调用的三个方法：预占 / 释放 / 实扣
    // ============================================================

    /**
     * 预占库存。订单进入待支付时调用。
     *
     * @throws BizException 可售不足时抛出，带具体剩余数量，收银员能直接告诉顾客
     */
    @Transactional
    public void lock(Long skuId, Long storeId, int qty, String bizNo) {
        if (qty <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "预占数量必须大于 0");
        }
        int rows = stockMapper.tryLock(skuId, storeId, qty);
        if (rows == 0) {
            InvStock current = findStock(skuId, storeId);
            if (current == null) {
                throw BizException.of(ErrorCode.STOCK_NOT_FOUND);
            }
            throw BizException.of(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("库存不足，当前可售 %d 件", current.getAvailable()));
        }
        writeLog(skuId, storeId, "LOCK", -qty, bizNo, "订单预占");
    }

    /** 释放预占。取消订单、支付超时、创建订单过程中途失败时调用。 */
    @Transactional
    public void release(Long skuId, Long storeId, int qty, String bizNo) {
        int rows = stockMapper.release(skuId, storeId, qty);
        if (rows == 0) {
            // 释放失败通常意味着重复释放或数据不一致，记日志但不阻断主流程
            log.warn("释放预占失败（可能已释放过）skuId={} qty={} bizNo={}", skuId, qty, bizNo);
            return;
        }
        writeLog(skuId, storeId, "RELEASE", qty, bizNo, "释放预占");
    }

    /** 实扣。支付成功后调用，把预占变成真正的出库。 */
    @Transactional
    public void commit(Long skuId, Long storeId, int qty, String bizNo) {
        int rows = stockMapper.commit(skuId, storeId, qty);
        if (rows == 0) {
            InvStock current = findStock(skuId, storeId);
            throw BizException.of(ErrorCode.DATA_CONFLICT,
                    "库存实扣失败，预占数据异常（当前锁定 " +
                            (current == null ? 0 : current.getLocked()) + " 件）");
        }
        writeLog(skuId, storeId, "COMMIT", 0, bizNo, "支付成功实扣");
    }

    // ============================================================
    //  补货 / 报损 / 盘点
    // ============================================================

    /**
     * 补货入库。
     *
     * @param items 每项 {skuId, qty, costPrice(可选)}
     */
    @Transactional
    public String replenish(List<BillItemForm> items, String remark) {
        return createBill("INBOUND", "IN", items, remark);
    }

    /** 报损：货坏了、过期了，从可售里扣掉 */
    @Transactional
    public String loss(List<BillItemForm> items, String remark) {
        return createBill("LOSS", "LS", items, remark);
    }

    /** 盘点：按实盘数覆盖 */
    @Transactional
    public String stockCheck(List<BillItemForm> items, String remark) {
        if (items == null || items.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "盘点明细不能为空");
        }
        StockBill bill = newBill("CHECK", "CK", remark, items.size());
        for (BillItemForm f : items) {
            int rows = stockMapper.applyCounted(f.getSkuId(), storeId(), f.getQty());
            if (rows == 0) {
                throw BizException.of(ErrorCode.DATA_CONFLICT,
                        "商品 " + f.getSkuId() + " 盘点数小于已预占数，请先处理未支付订单");
            }
            writeLog(f.getSkuId(), storeId(), "CHECK", 0, bill.getBillNo(), "盘点覆盖");
        }
        return bill.getBillNo();
    }

    private String createBill(String type, String prefix, List<BillItemForm> items, String remark) {
        if (items == null || items.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "单据明细不能为空");
        }

        StockBill bill = newBill(type, prefix, remark, items.size());
        long totalAmount = 0;

        for (BillItemForm f : items) {
            if (f.getQty() == null || f.getQty() <= 0) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "数量必须大于 0");
            }
            ensureStockRow(f.getSkuId(), storeId());

            int rows;
            if ("INBOUND".equals(type)) {
                rows = stockMapper.inbound(f.getSkuId(), storeId(), f.getQty());
            } else {
                rows = stockMapper.loss(f.getSkuId(), storeId(), f.getQty());
                if (rows == 0) {
                    InvStock s = findStock(f.getSkuId(), storeId());
                    throw BizException.of(ErrorCode.STOCK_NOT_ENOUGH,
                            "商品 " + f.getSkuId() + " 可售仅 " + (s == null ? 0 : s.getAvailable()) + " 件，报损数量超出");
                }
            }
            if (rows == 0) {
                throw BizException.of(ErrorCode.STOCK_NOT_FOUND);
            }

            Long cost = f.getCostPrice() != null ? f.getCostPrice() : costPriceOf(f.getSkuId());
            long amount = cost * f.getQty();
            totalAmount += amount;

            StockBillItem item = new StockBillItem();
            item.setBillId(bill.getId());
            item.setBillNo(bill.getBillNo());
            item.setSkuId(f.getSkuId());
            item.setQty(f.getQty());
            item.setCostPrice(cost);
            item.setAmount(amount);
            billItemMapper.insert(item);

            writeLog(f.getSkuId(), storeId(),
                    "INBOUND".equals(type) ? "INBOUND" : "LOSS",
                    "INBOUND".equals(type) ? f.getQty() : -f.getQty(),
                    bill.getBillNo(), "INBOUND".equals(type) ? "补货入库" : "报损出库");
        }

        // 成本金额回写到单据头
        jdbc.update("UPDATE stock_bill SET total_amount = ? WHERE id = ?", totalAmount, bill.getId());
        log.info("{} 单据 {} 已生效，{} 项，成本 {} 分", type, bill.getBillNo(), items.size(), totalAmount);
        return bill.getBillNo();
    }

    private StockBill newBill(String type, String prefix, String remark, int itemCount) {
        StockBill bill = new StockBill();
        bill.setBillNo(sequenceService.next(prefix));
        bill.setType(type);
        bill.setSource("MANUAL");
        bill.setStatus("CONFIRMED");
        bill.setTotalQty(itemCount);
        bill.setTotalAmount(0L);
        bill.setRemark(remark);
        bill.setOperatorId(UserContext.currentUserId());
        bill.setConfirmedAt(LocalDateTime.now());
        billMapper.insert(bill);
        return bill;
    }

    /** 库存行不存在时补一行（新商品第一次入库走这里） */
    private void ensureStockRow(Long skuId, Long storeId) {
        jdbc.update("""
                INSERT INTO inv_stock (sku_id, store_id, on_hand, available, locked, safety_stock, version, updated_at)
                VALUES (?, ?, 0, 0, 0, 10, 0, NOW())
                ON DUPLICATE KEY UPDATE sku_id = sku_id
                """, skuId, storeId);
    }

    // ============================================================
    //  查询
    // ============================================================

    public InvStock findStock(Long skuId, Long storeId) {
        return stockMapper.selectOne(Wrappers.<InvStock>lambdaQuery()
                .eq(InvStock::getSkuId, skuId)
                .eq(InvStock::getStoreId, storeId));
    }

    public Map<String, Object> stockDetail(Long skuId) {
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT s.id AS skuId, s.sku_code AS skuCode, s.name AS skuName, s.unit,
                       s.barcode, s.standard_price AS standardPrice,
                       COALESCE(st.on_hand, 0)  AS onHand,
                       COALESCE(st.available, 0) AS available,
                       COALESCE(st.locked, 0)    AS locked,
                       COALESCE(st.safety_stock, 10) AS safetyStock
                  FROM pms_sku s
                  LEFT JOIN inv_stock st ON st.sku_id = s.id AND st.store_id = ?
                 WHERE s.id = ? AND s.deleted = 0
                """, storeId(), skuId);
        if (list.isEmpty()) {
            throw BizException.of(ErrorCode.GOODS_NOT_FOUND);
        }
        return list.get(0);
    }

    public List<Map<String, Object>> warningList(int limit) {
        return stockMapper.selectWarning(storeId(), limit);
    }

    public List<Map<String, Object>> pageStock(String keyword, int page, int size) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbc.queryForList("""
                SELECT s.id AS skuId, s.sku_code AS skuCode, s.name AS skuName, s.unit, s.barcode,
                       s.standard_price AS standardPrice, s.cost_price AS costPrice,
                       COALESCE(st.on_hand, 0) AS onHand,
                       COALESCE(st.available, 0) AS available,
                       COALESCE(st.locked, 0) AS locked,
                       COALESCE(st.safety_stock, 10) AS safetyStock
                  FROM pms_sku s
                  LEFT JOIN inv_stock st ON st.sku_id = s.id AND st.store_id = ?
                 WHERE s.deleted = 0 AND (s.name LIKE ? OR s.sku_code LIKE ? OR s.barcode LIKE ?)
                 ORDER BY (COALESCE(st.available,0) < COALESCE(st.safety_stock,10)) DESC, s.id DESC
                 LIMIT ? OFFSET ?
                """, storeId(), like, like, like, size, (page - 1) * size);
    }

    public List<Map<String, Object>> logs(Long skuId, int limit) {
        return jdbc.queryForList("""
                SELECT id, change_type AS changeType, qty, before_on_hand AS beforeOnHand,
                       after_on_hand AS afterOnHand, biz_no AS bizNo, remark, created_at AS createdAt
                  FROM inv_stock_log
                 WHERE sku_id = ? AND store_id = ?
                 ORDER BY id DESC LIMIT ?
                """, skuId, storeId(), limit);
    }

    public List<Map<String, Object>> billPage(String type, int page, int size) {
        return jdbc.queryForList("""
                SELECT b.id, b.bill_no AS billNo, b.type, b.status, b.total_qty AS totalQty,
                       b.total_amount AS totalAmount, b.remark, b.created_at AS createdAt,
                       (SELECT COUNT(*) FROM stock_bill_item i WHERE i.bill_id = b.id) AS itemCount
                  FROM stock_bill b
                 WHERE (? IS NULL OR b.type = ?)
                 ORDER BY b.id DESC LIMIT ? OFFSET ?
                """, type, type, size, (page - 1) * size);
    }

    /** 一致性自检：答辩现场可以直接调这个证明没有超卖 */
    public Map<String, Object> invariants() {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        Integer broken = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_stock WHERE on_hand <> available + locked", Integer.class);
        Integer negative = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_stock WHERE available < 0 OR locked < 0 OR on_hand < 0", Integer.class);
        r.put("rowCount", jdbc.queryForObject("SELECT COUNT(*) FROM inv_stock", Integer.class));
        r.put("brokenInvariant", broken);
        r.put("negativeRow", negative);
        r.put("ok", broken != null && broken == 0 && negative != null && negative == 0);
        return r;
    }

    private Long costPriceOf(Long skuId) {
        Long cost = jdbc.queryForObject(
                "SELECT cost_price FROM pms_sku WHERE id = ?", Long.class, skuId);
        return cost == null ? 0L : cost;
    }

    private void writeLog(Long skuId, Long storeId, String type, int qty, String bizNo, String remark) {
        Integer onHand = jdbc.queryForObject(
                "SELECT COALESCE(on_hand,0) FROM inv_stock WHERE sku_id = ? AND store_id = ?",
                Integer.class, skuId, storeId);
        int after = onHand == null ? 0 : onHand;
        jdbc.update("""
                INSERT INTO inv_stock_log
                    (sku_id, store_id, change_type, qty, before_on_hand, after_on_hand, biz_no, remark, operator_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, skuId, storeId, type, qty, after - qty, after, bizNo, remark,
                UserContext.currentUserId());
    }

    private Long storeId() {
        Long s = UserContext.currentStoreId();
        return s == null ? 1L : s;
    }

    /** 单据明细入参 */
    @lombok.Data
    public static class BillItemForm {
        private Long skuId;
        private Integer qty;
        private Long costPrice;
        private String remark;
    }
}
