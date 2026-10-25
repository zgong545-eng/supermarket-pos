package com.supermarket.pos.module.h5.controller;

import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.common.context.LoginUser;
import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;
import com.supermarket.pos.module.member.mapper.MemMemberMapper;
import com.supermarket.pos.module.member.service.MemberService;
import com.supermarket.pos.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * M10 会员端登录。
 *
 * <p><b>演示版不发短信</b>：验证码固定 {@code 1234}，界面上直接写着。
 * 真实环境这里要接短信网关 + 验证码 5 分钟过期 + 同手机号 60 秒限流，
 * 但课堂演示没有短信通道，硬接会让演示跑不起来。这个取舍写在答辩材料里比藏着好。
 *
 * <p>没注册过的手机号自动注册成普通会员——演示时不用先跑去后台建会员，
 * 顾客直接扫码进门。这也是"单系统自治、不依赖外部"的一部分。
 */
@Slf4j
@RestController
@RequestMapping("/api/h5/auth")
@RequiredArgsConstructor
public class H5AuthController {

    /** 演示验证码。生产必须改成真短信。 */
    private static final String DEMO_CODE = "1234";

    private final JdbcTemplate jdbc;
    private final MemMemberMapper memberMapper;
    private final MemberService memberService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");

        if (phone == null || !phone.matches("1[3-9]\\d{9}")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请输入正确的手机号");
        }
        if (!DEMO_CODE.equals(code)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "验证码错误（演示环境固定为 " + DEMO_CODE + "）");
        }

        Map<String, Object> member = memberMapper.selectByPhoneOrNo(phone);
        Long memberId;
        boolean isNew = false;
        if (member == null) {
            memberId = memberService.register("顾客" + phone.substring(7), phone, 1L);
            member = memberMapper.selectByPhoneOrNo(phone);
            isNew = true;
        } else {
            memberId = ((Number) member.get("id")).longValue();
            Object status = member.get("status");
            if (status != null && ((Number) status).intValue() == 0) {
                throw BizException.of(ErrorCode.MEMBER_DISABLED);
            }
        }

        LoginUser user = new LoginUser();
        user.setUserId(memberId);
        user.setUsername(phone);
        user.setRealName(String.valueOf(member.get("name")));
        user.setStoreId(1L);
        user.setRoles(Set.of("MEMBER"));
        user.setPermissions(Set.of("h5:order"));
        String token = jwtUtil.issue(user);

        jdbc.update("UPDATE mem_member SET last_consume_at = NOW() WHERE id = ?", memberId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", token);
        r.put("memberId", memberId);
        r.put("name", member.get("name"));
        r.put("memberNo", member.get("memberNo"));
        r.put("levelName", member.get("levelName"));
        r.put("isNew", isNew);
        log.info("H5 登录：phone={} memberId={} 新会员={}", phone, memberId, isNew);
        return Result.ok(isNew ? "登录成功，已为你开通会员" : "登录成功", r);
    }
}
