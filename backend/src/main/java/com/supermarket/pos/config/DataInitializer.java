package com.supermarket.pos.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.supermarket.pos.module.system.domain.SysRole;
import com.supermarket.pos.module.system.domain.SysUser;
import com.supermarket.pos.module.system.mapper.SysRoleMapper;
import com.supermarket.pos.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建三个演示账号。
 *
 * <p>为什么不在 SQL 里插：密码必须 BCrypt 加密，SQL 算不了。
 * 写死一个密文也不行——不同批次加密同一个密码结果不同，容易踩坑。
 *
 * <p>已存在则跳过，所以重启不会重复创建，也不会覆盖改过的密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfAbsent("admin",   "admin123",   "系统管理员", "ADMIN",   "13800000001");
        createIfAbsent("manager", "manager123", "王经理",     "MANAGER", "13800000002");
        createIfAbsent("cashier", "cashier123", "李小收",     "CASHIER", "13800000003");

        // 建单号序列用不到预置数据，用到时 insert on duplicate 自动建
        log.info("演示账号就绪：admin/admin123  manager/manager123  cashier/cashier123");
    }

    private void createIfAbsent(String username, String rawPassword, String realName,
                                String roleCode, String phone) {
        Long exists = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            return;
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setStoreId(1L);
        user.setStatus(1);
        userMapper.insert(user);

        SysRole role = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, roleCode));
        if (role != null) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                    user.getId(), role.getId());
        }
        log.info("创建账号 {} / {}（角色 {}）", username, rawPassword, roleCode);
    }
}
