package com.supermarket.pos.module.product.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品 SKU。
 *
 * <p>价格说明：standard_price 是标价，member_price 是会员直接价（可空）。
 * 两者都是「分」。会员等级折扣不存这里，由促销引擎按等级实时算——
 * 否则改一次等级折扣要刷几万条商品记录。
 */
@Data
@TableName("pms_sku")
public class PmsSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skuCode;
    private String barcode;
    private String name;
    private Long categoryId;
    private String spec;
    private String unit;
    private String brand;

    /** 标准价（分） */
    private Long standardPrice;
    /** 会员价（分），空则走等级折扣 */
    private Long memberPrice;
    /** 成本价（分），用于算毛利 */
    private Long costPrice;

    /** PIECE 计件 / WEIGHT 称重 */
    private String weightType;

    /** 1 上架 0 下架 */
    private Integer status;

    private String imageUrl;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isWeighed() {
        return "WEIGHT".equals(weightType);
    }
}
