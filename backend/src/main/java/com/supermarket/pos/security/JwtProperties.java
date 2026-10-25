package com.supermarket.pos.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。密钥从环境变量注入，默认值仅供课堂演示。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pos.jwt")
public class JwtProperties {

    private String secret = "pos-supermarket-course-project-secret-key-2026-change-me";
    private int expireMinutes = 720;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
