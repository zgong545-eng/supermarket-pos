package com.supermarket.pos.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页返回体。
 *
 * <p>不直接把 MyBatis-Plus 的 IPage 吐给前端：那个对象里有一堆前端用不上的字段
 * （size/orders/optimizeCountSql…），接口契约会随着 MP 升级而变。
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long page;
    private long pageSize;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(page.getTotal());
        r.setPage(page.getCurrent());
        r.setPageSize(page.getSize());
        r.setRecords(page.getRecords());
        return r;
    }

    /** 实体分页转 VO 分页 */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> converter) {
        PageResult<V> r = new PageResult<>();
        r.setTotal(page.getTotal());
        r.setPage(page.getCurrent());
        r.setPageSize(page.getSize());
        List<V> list = new ArrayList<>();
        page.getRecords().forEach(e -> list.add(converter.apply(e)));
        r.setRecords(list);
        return r;
    }

    public static <T> PageResult<T> empty(long page, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(0);
        r.setPage(page);
        r.setPageSize(pageSize);
        r.setRecords(new ArrayList<>());
        return r;
    }
}
