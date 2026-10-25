package com.supermarket.pos.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.supermarket.pos.common.context.LoginUser;
import com.supermarket.pos.common.context.UserContext;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.system.domain.SysRole;
import com.supermarket.pos.module.system.domain.SysUser;
import com.supermarket.pos.module.system.dto.LoginRequest;
import com.supermarket.pos.module.system.mapper.SysPermissionMapper;
import com.supermarket.pos.module.system.mapper.SysRoleMapper;
import com.supermarket.pos.module.system.mapper.SysUserMapper;
import com.supermarket.pos.module.system.vo.LoginVO;
import com.supermarket.pos.security.JwtProperties;
import com.supermarket.pos.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录相关。
 *
 * <p>登录失败时不要提示"用户名不存在"——那等于告诉别人哪些账号有效。
 * 统一返回"用户名或密码错误"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginVO login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));

        LoginUser loginUser = buildLoginUser(user);
        String token = jwtUtil.issue(loginUser);

        log.info("用户登录成功：{} ({})", user.getUsername(), user.getRealName());
        return LoginVO.builder()
                .token(token)
                .expiresIn(jwtProperties.getExpireMinutes() * 60L)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .storeId(user.getStoreId())
                .roles(loginUser.getRoles())
                .permissions(loginUser.getPermissions())
                .build();
    }

    /** 当前登录人信息，前端刷新页面后用它恢复状态 */
    public LoginVO me() {
        LoginUser u = UserContext.require();
        return LoginVO.builder()
                .userId(u.getUserId())
                .username(u.getUsername())
                .realName(u.getRealName())
                .storeId(u.getStoreId())
                .roles(u.getRoles())
                .permissions(u.getPermissions())
                .build();
    }

    private LoginUser buildLoginUser(SysUser user) {
        Set<String> roles = roleMapper.selectByUserId(user.getId()).stream()
                .map(SysRole::getCode)
                .collect(Collectors.toSet());
        Set<String> perms = Set.copyOf(permissionMapper.selectCodesByUserId(user.getId()));

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setStoreId(user.getStoreId());
        loginUser.setRoles(roles);
        loginUser.setPermissions(perms);
        return loginUser;
    }
}
