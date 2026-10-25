package com.supermarket.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 超市收银管理系统 · 后端启动类。
 *
 * <p>当前只承载容器骨架自检（{@code /api/health}）。业务模块按 M01–M13 逐步接入，
 * 包结构约定见 {@code docs/00-团队执行手册.md}：
 * <pre>
 * com.supermarket.pos
 *   ├── common/        公共返回、异常、工具
 *   ├── config/        配置类
 *   └── module/
 *       └── product/   M02 商品：controller / service / service/impl / domain / mapper / dto / vo
 * </pre>
 */
@SpringBootApplication
public class PosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosApplication.class, args);
    }
}
