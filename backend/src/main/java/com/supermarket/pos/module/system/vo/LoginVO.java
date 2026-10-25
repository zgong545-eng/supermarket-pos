package com.supermarket.pos.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class LoginVO {

    private String token;
    /** 有效期（秒），前端可以据此做无感续期 */
    private long expiresIn;
    private Long userId;
    private String username;
    private String realName;
    private Long storeId;
    private Set<String> roles;
    private Set<String> permissions;
}
