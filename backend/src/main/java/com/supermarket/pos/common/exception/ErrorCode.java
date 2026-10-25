package com.supermarket.pos.common.exception;

import lombok.Getter;

/**
 * 错误码分段。
 *
 * <p>分段的用处：看到 4xxxx 就知道是交易支付的问题，不用猜是哪个模块抛的。
 * 前端也可以按首位做不同处理（比如 2xxxx 一律跳登录页）。
 *
 * <pre>
 *   1xxxx  通用/参数
 *   2xxxx  权限/认证
 *   3xxxx  商品/库存
 *   4xxxx  交易/支付/订单
 *   5xxxx  会员/营销
 *   9xxxx  系统内部
 * </pre>
 */
@Getter
public enum ErrorCode {

    // ---------- 通用 ----------
    SUCCESS(0, "成功"),
    PARAM_INVALID(10001, "参数不合法"),
    NOT_FOUND(10002, "数据不存在"),
    DATA_CONFLICT(10003, "数据已存在或被其他操作修改，请刷新后重试"),
    OPERATION_NOT_ALLOWED(10004, "当前状态不允许此操作"),
    RATE_LIMITED(10005, "操作太频繁，请稍后再试"),

    // ---------- 权限 ----------
    UNAUTHORIZED(20001, "未登录或登录已过期"),
    TOKEN_INVALID(20002, "登录凭证无效"),
    TOKEN_EXPIRED(20003, "登录已过期，请重新登录"),
    FORBIDDEN(20004, "没有权限执行此操作"),
    LOGIN_FAILED(20005, "用户名或密码错误"),
    ACCOUNT_DISABLED(20006, "账号已停用，请联系管理员"),

    // ---------- 商品 / 库存 ----------
    GOODS_NOT_FOUND(30001, "商品不存在或已下架"),
    GOODS_CODE_DUPLICATED(30002, "商品编码已存在"),
    BARCODE_DUPLICATED(30003, "条码已被占用"),
    STOCK_NOT_ENOUGH(30004, "库存不足"),
    STOCK_CONFLICT(30005, "库存被同时修改，请重试"),
    STOCK_NOT_FOUND(30006, "该商品还没有库存记录"),
    BILL_ALREADY_CONFIRMED(30007, "单据已生效，不能重复提交"),
    SKU_OFF_SHELF(30008, "商品已下架，不能销售"),

    // ---------- 交易 / 支付 ----------
    ORDER_NOT_FOUND(40001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(40002, "订单当前状态不允许该操作"),
    ORDER_ALREADY_PAID(40003, "订单已支付，不能重复支付"),
    ORDER_AMOUNT_MISMATCH(40004, "支付金额与订单应收不一致"),
    PAY_DUPLICATED(40005, "该笔支付已处理过，本次回调已忽略"),
    PAY_NOT_FOUND(40006, "支付单不存在"),
    PAY_CHANNEL_DISABLED(40007, "该支付方式未启用"),
    REFUND_AMOUNT_EXCEED(40008, "退款金额超过可退金额"),
    SHIFT_NOT_OPEN(40009, "当前没有进行中的班次，请先开班"),
    SHIFT_ALREADY_CLOSED(40010, "班次已交班"),
    HOLD_NOT_FOUND(40011, "挂单不存在或已取走"),
    CART_EMPTY(40012, "购物车为空"),

    // ---------- 会员 / 营销 ----------
    MEMBER_NOT_FOUND(50001, "会员不存在"),
    MEMBER_PHONE_DUPLICATED(50002, "该手机号已注册会员"),
    MEMBER_DISABLED(50003, "会员已冻结"),
    POINT_NOT_ENOUGH(50004, "积分不足"),
    POINT_DEDUCT_EXCEED(50005, "积分抵扣超过上限"),
    WALLET_BALANCE_NOT_ENOUGH(50006, "储值余额不足"),
    COUPON_NOT_FOUND(50007, "优惠券不存在"),
    COUPON_USED(50008, "优惠券已使用"),
    COUPON_EXPIRED(50009, "优惠券已过期"),
    COUPON_NOT_MATCH(50010, "订单不满足优惠券使用条件"),
    ACTIVITY_NOT_FOUND(50011, "活动不存在或已结束"),

    // ---------- 系统 ----------
    SYSTEM_ERROR(99999, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
