package com.supermarket.pos.health;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 容器骨架自检接口。
 *
 * <p>作用只有一个：证明"后端 → MySQL → Redis"三条链路真的通了。
 * 状态页（nginx 托管）每 5 秒拉一次，全绿就说明环境没问题，可以开始写业务。
 *
 * <p>为什么不用 actuator 的 /health 就够了：actuator 只回 UP/DOWN，
 * 不告诉你 MySQL 的版本号和耗时。自检接口把细节摊开，出问题时不用再去翻日志。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public HealthController(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> mysql = probeMysql();
        Map<String, Object> redisProbe = probeRedis();

        boolean allOk = Boolean.TRUE.equals(mysql.get("ok")) && Boolean.TRUE.equals(redisProbe.get("ok"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allOk ? "UP" : "DEGRADED");
        body.put("app", "supermarket-pos");
        body.put("time", LocalDateTime.now().format(TS));
        body.put("mysql", mysql);
        body.put("redis", redisProbe);
        return body;
    }

    /** 顺便给个纯文本探针，curl 一下就知道活着没 */
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    private Map<String, Object> probeMysql() {
        long start = System.nanoTime();
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String version = jdbc.queryForObject("SELECT VERSION()", String.class);
            String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
            r.put("ok", true);
            r.put("detail", version + " / " + database);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("detail", rootMessage(e));
        }
        r.put("ms", elapsedMs(start));
        return r;
    }

    private Map<String, Object> probeRedis() {
        long start = System.nanoTime();
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            r.put("ok", true);
            r.put("detail", pong == null ? "PONG" : pong);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("detail", rootMessage(e));
        }
        r.put("ms", elapsedMs(start));
        return r;
    }

    private long elapsedMs(long startNano) {
        return Math.max(1L, (System.nanoTime() - startNano) / 1_000_000L);
    }

    /** 取最内层异常信息，去掉长长的包装链，免得状态页被刷屏 */
    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = cur.getClass().getSimpleName();
        }
        msg = msg.replaceAll("\\s+", " ").trim();
        return msg.length() > 160 ? msg.substring(0, 160) + "…" : msg;
    }
}
