package com.supermarket.pos.module.system.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.system.dto.LoginRequest;
import com.supermarket.pos.module.system.service.AuthService;
import com.supermarket.pos.module.system.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 登录接口。唯一不需要 token 的业务接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok("登录成功", authService.login(req));
    }

    /** 刷新页面后前端用它恢复用户态；token 过期会直接被安全层拦成 401 */
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.ok(authService.me());
    }

    /**
     * 登出。
     * 无状态 JWT 没有服务端会话可清，前端删掉本地 token 即可。
     * 这里保留接口是为了前端调用链完整，将来要加黑名单也从这里加。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
