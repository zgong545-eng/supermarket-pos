package com.supermarket.pos.module.shift.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.common.context.LoginUser;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.module.promotion.dto.CartRequest;
import com.supermarket.pos.module.promotion.engine.PromotionEngine;
import com.supermarket.pos.module.promotion.service.PromotionService;
import com.supermarket.pos.module.shift.domain.PosHold;
import com.supermarket.pos.module.shift.mapper.PosHoldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M08 挂单服务。
 *
 * <p>挂单不占库存，理由写在 {@link PosHold} 的类注释里。
 * 因为不占库存，取单时商品可能已经卖光了——这不是 bug，是设计如此：
 * 取单时重新算钱、重新预占库存，占不到就明确告诉收银员"某商品只剩 N 件"。
 *
 * <p>挂单金额是<b>挂起那一刻</b>算出来的，仅用于列表展示。
 * 真正收钱时一定重新计算（活动可能已经结束了），所以 {@code totalAmount}
 * 从不当成应收金额用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final PosHoldMapper holdMapper;
    private final SequenceService sequenceService;
    private final PromotionService promotionService;
    private final JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 挂起当前购物车。
     *
     * @param req 完整购物车（跟下单用同一个 DTO，前端可以直接把购物车对象丢过来）
     */
    @Transactional
    public Map<String, Object> hold(CartRequest req) {
        LoginUser user = UserContext.require();
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "空购物车不能挂单");
        }

        // 用促销引擎算一遍，得到"挂起时"的金额和商品名，列表里能直接展示
        PromotionEngine.Result calc = promotionService.calculate(req, false);

        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (PromotionEngine.CartLine line : calc.getLines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", line.getSkuId());
            m.put("skuCode", line.getSkuCode());
            m.put("skuName", line.getSkuName());
            m.put("barcode", line.getBarcode());
            m.put("unit", line.getUnit());
            m.put("qty", line.getQty());
            m.put("weight", line.getWeight());
            m.put("salePrice", line.getSalePrice());
            m.put("netAmount", line.getNetAmount());
            snapshot.add(m);
        }

        PosHold hold = new PosHold();
        hold.setHoldNo(sequenceService.next("HD"));
        hold.setStoreId(storeId());
        hold.setCashierId(user.getUserId());
        try {
            hold.setItemsJson(JSON.writeValueAsString(Map.of(
                    "items", snapshot,
                    "memberId", req.getMemberId() == null ? 0 : req.getMemberId()
            )));
        } catch (Exception e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "挂单快照序列化失败");
        }
        hold.setTotalAmount(calc.getPayableAmount());
        hold.setStatus("HELD");
        hold.setCreatedAt(LocalDateTime.now());
        holdMapper.insert(hold);

        log.info("挂单：{} 收银员={} 商品数={} 金额={} 分",
                hold.getHoldNo(), user.getUsername(), snapshot.size(), hold.getTotalAmount());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("holdNo", hold.getHoldNo());
        r.put("itemCount", snapshot.size());
        r.put("totalAmount", hold.getTotalAmount());
        return r;
    }

    /** 取单：返回购物车快照，前端拿回去重建购物车 */
    @Transactional
    public Map<String, Object> resume(String holdNo) {
        PosHold hold = mustGet(holdNo);
        if (!"HELD".equals(hold.getStatus())) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该挂单状态是「" + hold.getStatus() + "」，不能再取出");
        }
        int n = jdbc.update("""
                UPDATE pos_hold SET status = 'RESUMED', resumed_at = NOW()
                 WHERE hold_no = ? AND status = 'HELD'
                """, holdNo);
        if (n == 0) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED, "挂单已被取走或作废");
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("holdNo", hold.getHoldNo());
        r.put("totalAmount", hold.getTotalAmount());
        try {
            r.put("snapshot", JSON.readValue(hold.getItemsJson(), Map.class));
        } catch (Exception e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "挂单快照解析失败");
        }
        return r;
    }

    /** 作废挂单（顾客不要了） */
    @Transactional
    public void cancel(String holdNo) {
        int n = jdbc.update("""
                UPDATE pos_hold SET status = 'CANCELLED'
                 WHERE hold_no = ? AND status = 'HELD'
                """, holdNo);
        if (n == 0) {
            throw BizException.of(ErrorCode.OPERATION_NOT_ALLOWED, "挂单不存在或已处理");
        }
    }

    /** 当前收银员挂起中的单子 */
    public List<Map<String, Object>> list() {
        LoginUser user = UserContext.require();
        return jdbc.queryForList("""
                SELECT hold_no AS holdNo, total_amount AS totalAmount, created_at AS createdAt,
                       TIMESTAMPDIFF(MINUTE, created_at, NOW()) AS minutes
                  FROM pos_hold
                 WHERE cashier_id = ? AND status = 'HELD'
                 ORDER BY id DESC
                """, user.getUserId());
    }

    /** 挂单数量，收银台角标用 */
    public int count() {
        LoginUser user = UserContext.require();
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_hold WHERE cashier_id = ? AND status = 'HELD'",
                Integer.class, user.getUserId());
        return n == null ? 0 : n;
    }

    private PosHold mustGet(String holdNo) {
        List<PosHold> list = holdMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PosHold>()
                        .eq(PosHold::getHoldNo, holdNo)
                        .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw BizException.of(ErrorCode.NOT_FOUND, "挂单不存在");
        }
        return list.get(0);
    }

    private Long storeId() {
        Long id = UserContext.currentStoreId();
        return id == null ? 1L : id;
    }
}
