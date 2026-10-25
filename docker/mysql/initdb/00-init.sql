-- ============================================================
--  初始化脚本
--  执行时机：数据卷为空时，容器第一次启动自动执行一次
--  执行顺序：官方镜像已创建 MYSQL_DATABASE 和 MYSQL_USER 之后
--
--  ⚠️ 这里只建库、调字符集、给权限。
--     42 张业务表不写在这里 —— 表结构由 Flyway 管理（backend/src/main/resources/db/migration），
--     这样表结构变更才有版本记录，答辩时能拿出"每次改动都有据可查"的证据。
-- ============================================================

-- ---------- 主库字符集校正 ----------
ALTER DATABASE `pos` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------- 测试库 ----------
-- 跑集成测试用，跟开发数据隔离，避免测试把演示数据冲掉
CREATE DATABASE IF NOT EXISTS `pos_test`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- ---------- 授权 ----------
-- 应用账号对两个库都有权限；root 只留给管理员用
GRANT ALL PRIVILEGES ON `pos`.*      TO 'pos'@'%';
GRANT ALL PRIVILEGES ON `pos_test`.* TO 'pos'@'%';
FLUSH PRIVILEGES;

-- ---------- 自检标记 ----------
-- 用 SELECT 确认初始化跑过了：
--   docker compose exec mysql mysql -upos -p pos -e "SHOW TABLES;"
CREATE TABLE IF NOT EXISTS `pos`.`sys_init_marker` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `marker`      VARCHAR(64)  NOT NULL                COMMENT '标记名',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_marker` (`marker`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '容器初始化标记（仅用于自检，业务不依赖）';

INSERT IGNORE INTO `pos`.`sys_init_marker` (`marker`) VALUES ('docker-init-v1');
