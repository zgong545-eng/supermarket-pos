package com.supermarket.pos.common.api;

import com.supermarket.pos.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体。
 *
 * <p>全系统只有这一种外壳，前端只写一次解析逻辑。
 * 约定：{@code code == 0} 表示成功，非 0 一律是失败，{@code message} 可以直接弹给用户看。
 *
 * <pre>
 * { "code": 0, "message": "ok", "data": {...}, "traceId": "..." }
 * </pre>
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return of(0, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return of(0, "ok", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return of(0, message, data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return of(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return of(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return of(code, message, null);
    }

    private static <T> Result<T> of(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
