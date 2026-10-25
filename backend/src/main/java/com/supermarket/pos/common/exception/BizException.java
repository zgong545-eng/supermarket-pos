package com.supermarket.pos.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>凡是"用户能看懂、且应该被告知"的失败，都抛这个。
 * 不要抛 RuntimeException 了事——那样前端只能拿到一句"系统错误"，
 * 收银员在柜台前不知道该怎么办。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 覆盖默认文案，用于带具体数字的场景，例如"库存不足，仅剩 3 件" */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }
}
