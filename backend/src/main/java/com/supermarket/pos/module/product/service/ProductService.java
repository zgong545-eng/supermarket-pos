package com.supermarket.pos.module.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.supermarket.pos.common.api.PageResult;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.product.domain.PmsCategory;
import com.supermarket.pos.module.product.domain.PmsSku;
import com.supermarket.pos.module.product.mapper.PmsCategoryMapper;
import com.supermarket.pos.module.product.mapper.PmsSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品服务。
 *
 * <p>改价必须留痕：{@code pms_price_change} 记录谁、什么时候、从多少改到多少、为什么。
 * 答辩时被问"价格是不是随便改的"，把这张表翻出来就行。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final PmsSkuMapper skuMapper;
    private final PmsCategoryMapper categoryMapper;
    private final JdbcTemplate jdbc;

    public PageResult<PmsSku> page(String keyword, Long categoryId, Integer status, long page, long size) {
        var q = Wrappers.<PmsSku>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PmsSku::getName, keyword).or()
                        .like(PmsSku::getSkuCode, keyword).or()
                        .like(PmsSku::getBarcode, keyword))
                .eq(categoryId != null, PmsSku::getCategoryId, categoryId)
                .eq(status != null, PmsSku::getStatus, status)
                .orderByDesc(PmsSku::getId);
        IPage<PmsSku> result = skuMapper.selectPage(new Page<>(page, size), q);
        return PageResult.of(result);
    }

    public PmsSku detail(Long id) {
        PmsSku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw BizException.of(ErrorCode.GOODS_NOT_FOUND);
        }
        return sku;
    }

    /** 扫码取商品：收银台的核心查询 */
    public PmsSku findByBarcode(String barcode) {
        PmsSku sku = skuMapper.selectByAnyBarcode(barcode);
        if (sku == null) {
            throw BizException.of(ErrorCode.GOODS_NOT_FOUND, "条码 " + barcode + " 没有匹配到商品");
        }
        return sku;
    }

    @Transactional
    public Long create(PmsSku sku) {
        validateSku(sku, null);

        // 编码和条码都要查重，否则会出现两个商品同一个条码，扫码时谁先谁后说不清
        Long sameCode = skuMapper.selectCount(
                Wrappers.<PmsSku>lambdaQuery().eq(PmsSku::getSkuCode, sku.getSkuCode()));
        if (sameCode != null && sameCode > 0) {
            throw BizException.of(ErrorCode.GOODS_CODE_DUPLICATED);
        }
        assertBarcodeFree(sku.getBarcode());

        sku.setId(null);
        if (sku.getStatus() == null) {
            sku.setStatus(1);
        }
        if (sku.getCostPrice() == null) {
            sku.setCostPrice(0L);
        }
        if (sku.getWeightType() == null) {
            sku.setWeightType("PIECE");
        }
        skuMapper.insert(sku);

        if (StringUtils.hasText(sku.getBarcode())) {
            jdbc.update("INSERT IGNORE INTO pms_sku_barcode (sku_id, barcode, type) VALUES (?,?,?)",
                    sku.getId(), sku.getBarcode(), sku.isWeighed() ? "WEIGHT" : "NORMAL");
        }
        return sku.getId();
    }

    @Transactional
    public void update(Long id, PmsSku form) {
        PmsSku old = detail(id);
        validateSku(form, id);

        if (StringUtils.hasText(form.getBarcode())
                && !form.getBarcode().equals(old.getBarcode())) {
            assertBarcodeFree(form.getBarcode());
            jdbc.update("INSERT IGNORE INTO pms_sku_barcode (sku_id, barcode, type) VALUES (?,?,?)",
                    id, form.getBarcode(), "NORMAL");
        }

        form.setId(id);
        form.setSkuCode(old.getSkuCode());   // 编码不允许改，改了历史订单就对不上了
        skuMapper.updateById(form);
    }

    /** 单独改价：必须留痕 */
    @Transactional
    public void changePrice(Long id, Long newPrice, String reason) {
        if (newPrice == null || newPrice < 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "价格不能为负");
        }
        PmsSku sku = detail(id);
        if (sku.getStandardPrice().equals(newPrice)) {
            return;
        }
        jdbc.update("UPDATE pms_sku SET standard_price = ?, updated_at = NOW() WHERE id = ?",
                newPrice, id);
        jdbc.update("""
                INSERT INTO pms_price_change (sku_id, old_price, new_price, reason, operator_id)
                VALUES (?,?,?,?,?)
                """, id, sku.getStandardPrice(), newPrice, reason, UserContext.currentUserId());
    }

    /** 分类树（只有两级，够用且简单） */
    public List<Map<String, Object>> categoryTree() {
        List<PmsCategory> all = categoryMapper.selectList(
                Wrappers.<PmsCategory>lambdaQuery()
                        .eq(PmsCategory::getStatus, 1)
                        .orderByAsc(PmsCategory::getSort));
        Map<Long, Map<String, Object>> roots = new LinkedHashMap<>();
        for (PmsCategory c : all) {
            if (c.getParentId() == null || c.getParentId() == 0L) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", c.getId());
                node.put("name", c.getName());
                node.put("children", new ArrayList<Map<String, Object>>());
                roots.put(c.getId(), node);
            }
        }
        for (PmsCategory c : all) {
            Map<String, Object> parent = roots.get(c.getParentId());
            if (parent != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", c.getId());
                node.put("name", c.getName());
                children.add(node);
            }
        }
        return new ArrayList<>(roots.values());
    }

    private void validateSku(PmsSku sku, Long selfId) {
        if (!StringUtils.hasText(sku.getName())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "商品名称不能为空");
        }
        if (sku.getStandardPrice() == null || sku.getStandardPrice() < 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "标准价必须是非负数（单位：分）");
        }
        if (sku.getMemberPrice() != null && sku.getMemberPrice() > sku.getStandardPrice()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "会员价不能高于标准价");
        }
        if (StringUtils.hasText(sku.getBarcode())) {
            // 条码只能是数字，长度 8-14（EAN-13 / 店内码）
            if (!sku.getBarcode().matches("\\d{8,14}")) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "条码必须是 8-14 位数字");
            }
        }
    }

    private void assertBarcodeFree(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pms_sku_barcode WHERE barcode = ?", Integer.class, barcode);
        if (n != null && n > 0) {
            throw BizException.of(ErrorCode.BARCODE_DUPLICATED, "条码 " + barcode + " 已被其他商品占用");
        }
    }
}
