package com.supermarket.pos.config;

import com.supermarket.pos.module.demo.service.DemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 空库自动灌演示数据。
 *
 * <p>为什么需要它：答辩时最怕打开页面全是 0——
 * "这个系统还没数据"讲不下去，现场灌数据又显得手忙脚乱。
 * 所以只要库是空的（没有商品），启动时自动准备：
 * 60 个商品 + 3 个会员 + 活动 + 券 + 30 天历史订单。
 *
 * <p>为什么不放进 Flyway 的 V3 脚本：数据脚本进了迁移就没法"重置"了，
 * 而且一个课程项目每次启动都跑几千行 INSERT 也太重。
 * 放在 ApplicationRunner 里，只在首启执行，还能用 {@code pos.demo.enabled} 一键关掉。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final DemoService demoService;

    @Value("${pos.demo.enabled:false}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("演示数据自动灌入已关闭（pos.demo.enabled=false），不影响正常使用");
            return;
        }

        Integer skuCount = jdbc.queryForObject("SELECT COUNT(*) FROM pms_sku", Integer.class);
        if (skuCount != null && skuCount > 0) {
            log.info("库里已有 {} 个商品，跳过演示数据初始化", skuCount);
            return;
        }

        log.info("检测到空库，开始灌演示数据……");
        long t0 = System.currentTimeMillis();
        demoService.seedBase();
        demoService.seedOrders(30, 300);
        log.info("演示数据准备完成，用时 {} ms。可以打开页面看报表了", System.currentTimeMillis() - t0);
    }
}
