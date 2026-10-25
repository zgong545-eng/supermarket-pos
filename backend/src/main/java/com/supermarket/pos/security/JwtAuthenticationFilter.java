package com.supermarket.pos.security;

import com.supermarket.pos.common.context.LoginUser;
import com.supermarket.pos.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从请求头取 Bearer token，解析后写入 SecurityContext 与 UserContext。
 *
 * <p>解析失败不在这里返回错误，只是不设置认证信息 —— 让后面的
 * SecurityConfig 决定这个路径到底是"必须登录"还是"匿名可访问"。
 * 这样 /api/auth/login 这种公开接口不会被误伤。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties props;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                LoginUser user = jwtUtil.parse(token);
                if (user != null) {
                    UserContext.set(user);

                    // 权限码直接当 authority 用，配合 @PreAuthorize("hasAuthority('goods:edit')")
                    List<SimpleGrantedAuthority> authorities = user.getPermissions().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            chain.doFilter(request, response);
        } finally {
            // 一定要清：Tomcat 复用线程，不清会串号
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(props.getHeader());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = props.getPrefix();
        if (StringUtils.hasText(prefix) && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
