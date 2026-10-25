package com.supermarket.pos.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户（线程内可见）。
 *
 * <p>由 JWT 过滤器在请求进来时写入，请求结束时清除。
 * 业务代码里要拿当前操作人，统一用 {@link UserContext#get()}，
 * 不要在业务里再解析一遍 token。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String realName;
    private Long storeId;
    private Set<String> roles;
    private Set<String> permissions;

    public Set<String> getRoles() {
        return roles == null ? Collections.emptySet() : roles;
    }

    public Set<String> getPermissions() {
        return permissions == null ? Collections.emptySet() : permissions;
    }

    public boolean isAdmin() {
        return getRoles().contains("ADMIN");
    }
}
