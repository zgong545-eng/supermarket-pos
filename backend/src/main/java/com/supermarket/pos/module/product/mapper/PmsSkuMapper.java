package com.supermarket.pos.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supermarket.pos.module.product.domain.PmsSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmsSkuMapper extends BaseMapper<PmsSku> {

    /**
     * 扫码查询：主条码和副条码都要能扫出来。
     *
     * <p>为什么用 UNION 而不是两次查询：收银台每扫一次都走这个接口，
     * 一次往返比两次快，而且能保证"主码优先"的确定性。
     */
    @Select("""
            SELECT s.* FROM pms_sku s
            WHERE s.deleted = 0 AND s.status = 1
              AND (s.barcode = #{barcode}
                   OR EXISTS (SELECT 1 FROM pms_sku_barcode b
                              WHERE b.sku_id = s.id AND b.barcode = #{barcode}))
            ORDER BY (s.barcode = #{barcode}) DESC
            LIMIT 1
            """)
    PmsSku selectByAnyBarcode(@Param("barcode") String barcode);
}
