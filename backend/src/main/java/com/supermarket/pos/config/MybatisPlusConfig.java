package com.supermarket.pos.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>两个拦截器：
 * <ul>
 *   <li>分页：自动改写 count / limit，不用手写 LIMIT</li>
 *   <li>乐观锁：实体上 {@code @Version} 的字段会自动带上 version 条件，
 *       库存并发扣减靠它，UPDATE 影响 0 行就说明被人抢先改了</li>
 * </ul>
 */
@Configuration
@MapperScan("com.supermarket.pos.module.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁必须加在分页之前，否则分页参数的解析会有问题
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);      // 单页最多 500 条，防止有人 pageSize=100000 拖死库
        pagination.setOverflow(false);     // 页码越界返回空，不回到第一页
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
