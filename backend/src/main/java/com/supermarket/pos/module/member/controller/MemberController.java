package com.supermarket.pos.module.member.controller;

import com.supermarket.pos.common.api.PageResult;
import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** M04 会员接口 */
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('member:view')")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(memberService.page(keyword, page, size));
    }

    /** 收银台报手机号识别会员 */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('member:view','pos:checkout')")
    public Result<Map<String, Object>> lookup(@RequestParam String key) {
        return Result.ok(memberService.lookup(key));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('member:view')")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(memberService.detail(id));
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('member:edit')")
    public Result<Long> register(@RequestParam String name,
                                 @RequestParam String phone,
                                 @RequestParam(required = false) Long levelId) {
        return Result.ok("开卡成功", memberService.register(name, phone, levelId));
    }

    @PostMapping("/{id}/recharge")
    @PreAuthorize("hasAuthority('member:recharge')")
    public Result<Long> recharge(@PathVariable Long id,
                                 @RequestParam Long amount,
                                 @RequestParam(required = false) String remark) {
        return Result.ok("充值成功", memberService.recharge(id, amount, remark));
    }

    @GetMapping("/{id}/points-log")
    @PreAuthorize("hasAuthority('member:view')")
    public Result<List<Map<String, Object>>> pointsLog(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(memberService.pointsLog(id, limit));
    }

    @GetMapping("/{id}/wallet-log")
    @PreAuthorize("hasAuthority('member:view')")
    public Result<List<Map<String, Object>>> walletLog(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(memberService.walletLog(id, limit));
    }

    /** 收银台选券：只返回未使用且未过期的 */
    @GetMapping("/{id}/coupons")
    @PreAuthorize("hasAnyAuthority('member:view','pos:checkout')")
    public Result<List<Map<String, Object>>> coupons(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "true") boolean usableOnly) {
        return Result.ok(memberService.coupons(id, usableOnly));
    }

    @PostMapping("/{id}/coupons/{couponId}")
    @PreAuthorize("hasAuthority('member:edit')")
    public Result<Void> issueCoupon(@PathVariable Long id, @PathVariable Long couponId) {
        memberService.issueCoupon(id, couponId);
        return Result.ok("领券成功", null);
    }

    /** 会员端自助领券 */
    @PostMapping("/coupons/{couponId}/claim")
    public Result<Void> claimCoupon(@PathVariable Long couponId,
                                    @RequestParam Long memberId) {
        memberService.issueCoupon(memberId, couponId);
        return Result.ok("领券成功", null);
    }

    @GetMapping("/coupon-templates")
    public Result<List<Map<String, Object>>> templates() {
        return Result.ok(memberService.couponTemplates());
    }
}
