package com.supermarket.pos.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supermarket.pos.module.stock.domain.InvStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存操作。
 *
 * <p><b>关键设计：所有数量变动都是一条带条件的 UPDATE，不是"读出来改再写回"。</b>
 *
 * <p>为什么这样做：读-改-写在并发下必然出问题。
 * 两个人同时把 available=5 读进内存，各自减 3 再写回，结果 available 变成 2，
 * 但实际卖出了 6 件——超卖了 4 件，而且报错都没有。
 *
 * <p>把判断写进 WHERE（{@code AND available >= #{qty}}），数据库行锁会保证
 * 只有一个线程成功，另一个拿到"影响 0 行"，我们据此抛「库存不足」。
 * 这比"乐观锁 version 冲突后重试 3 次"更直接：**根本不存在冲突后的中间状态**。
 *
 * <p>version 字段仍然保留，用于那些不得不先读后写的场景（例如盘点要整行覆盖）。
 */
@Mapper
public interface InvStockMapper extends BaseMapper<InvStock> {

    /**
     * 预占：可售减少，锁定增加。总量不变。
     *
     * @return 1 成功；0 表示可售不足（或没这行库存）
     */
    @Update("""
            UPDATE inv_stock
               SET available = available - #{qty},
                   locked    = locked + #{qty},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
               AND available >= #{qty}
            """)
    int tryLock(@Param("skuId") Long skuId, @Param("storeId") Long storeId, @Param("qty") int qty);

    /**
     * 释放：订单取消 / 超时，把预占还回去。
     * 必须带 {@code locked >= qty}，防止把别人的预占放掉。
     */
    @Update("""
            UPDATE inv_stock
               SET available = available + #{qty},
                   locked    = locked - #{qty},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
               AND locked >= #{qty}
            """)
    int release(@Param("skuId") Long skuId, @Param("storeId") Long storeId, @Param("qty") int qty);

    /**
     * 实扣：付款成功后，把预占变成真正的出库。
     * 总量减少、锁定减少，可售不变——因为货早就被预占走了。
     */
    @Update("""
            UPDATE inv_stock
               SET on_hand  = on_hand - #{qty},
                   locked   = locked - #{qty},
                   version  = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
               AND locked >= #{qty} AND on_hand >= #{qty}
            """)
    int commit(@Param("skuId") Long skuId, @Param("storeId") Long storeId, @Param("qty") int qty);

    /** 入库（补货）：总量和可售同时增加 */
    @Update("""
            UPDATE inv_stock
               SET on_hand = on_hand + #{qty},
                   available = available + #{qty},
                   version = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
            """)
    int inbound(@Param("skuId") Long skuId, @Param("storeId") Long storeId, @Param("qty") int qty);

    /** 报损：总量和可售同时减少，且不能减成负数 */
    @Update("""
            UPDATE inv_stock
               SET on_hand = on_hand - #{qty},
                   available = available - #{qty},
                   version = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
               AND available >= #{qty}
            """)
    int loss(@Param("skuId") Long skuId, @Param("storeId") Long storeId, @Param("qty") int qty);

    /**
     * 盘点：按实盘数覆盖账面，并把可售重算成「实盘 - 已预占」。
     *
     * <p>这是唯一允许"整行覆盖"的操作，所以带 {@code on_hand = version} 式的乐观锁不够，
     * 直接用 {@code counted >= locked} 保证不会把已预占的货盘没。
     */
    @Update("""
            UPDATE inv_stock
               SET on_hand = #{counted},
                   available = #{counted} - locked,
                   version = version + 1,
                   updated_at = NOW()
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
               AND #{counted} >= locked
            """)
    int applyCounted(@Param("skuId") Long skuId, @Param("storeId") Long storeId,
                     @Param("counted") int counted);

    @Select("""
            SELECT * FROM inv_stock
             WHERE sku_id = #{skuId} AND store_id = #{storeId}
             FOR UPDATE
            """)
    InvStock selectForUpdate(@Param("skuId") Long skuId, @Param("storeId") Long storeId);

    /** 库存不足明细：低于安全库存的，按缺口从大到小排（补货界面直接用） */
    @Select("""
            SELECT s.id AS skuId, s.sku_code AS skuCode, s.barcode, s.name AS skuName,
                   s.unit, s.standard_price AS standardPrice, s.cost_price AS costPrice,
                   st.id AS stockId, st.on_hand AS onHand, st.available,
                   st.locked, st.safety_stock AS safetyStock,
                   (st.safety_stock - st.available) AS shortage
              FROM inv_stock st
              JOIN pms_sku s ON s.id = st.sku_id AND s.deleted = 0
             WHERE st.store_id = #{storeId}
               AND st.available < st.safety_stock
             ORDER BY shortage DESC
             LIMIT #{limit}
            """)
    List<java.util.Map<String, Object>> selectWarning(@Param("storeId") Long storeId,
                                                     @Param("limit") int limit);
}
