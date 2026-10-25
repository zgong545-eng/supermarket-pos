package com.supermarket.pos.security;

import com.supermarket.pos.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>把角色和权限码直接写进 token：这样每个请求不用再查一遍数据库。
 * 代价是改了权限要重新登录才生效——课堂演示场景可以接受。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_REAL_NAME = "rname";
    private static final String CLAIM_STORE = "store";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(LoginUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getExpireMinutes() * 60L);
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_REAL_NAME, user.getRealName())
                .claim(CLAIM_STORE, user.getStoreId())
                .claim(CLAIM_ROLES, List.copyOf(user.getRoles()))
                .claim(CLAIM_PERMS, List.copyOf(user.getPermissions()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token。
     *
     * @return 解析成功返回用户；签名不对 / 过期返回 null（由过滤器决定怎么响应）
     */
    public LoginUser parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            LoginUser u = new LoginUser();
            u.setUserId(Long.valueOf(c.getSubject()));
            u.setUsername(c.get(CLAIM_USERNAME, String.class));
            u.setRealName(c.get(CLAIM_REAL_NAME, String.class));
            Number store = c.get(CLAIM_STORE, Number.class);
            u.setStoreId(store == null ? null : store.longValue());
            u.setRoles(toSet(c.get(CLAIM_ROLES)));
            u.setPermissions(toSet(c.get(CLAIM_PERMS)));
            return u;
        } catch (ExpiredJwtException e) {
            log.debug("token 已过期");
            return null;
        } catch (Exception e) {
            log.debug("token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> toSet(Object claim) {
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
