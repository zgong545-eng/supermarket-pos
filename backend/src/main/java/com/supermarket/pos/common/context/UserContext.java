package com.supermarket.pos.common.context;

import com.supermarket.pos.common.exception.BizException;
import com.supermarket.pos.common.exception.ErrorCode;

/**
 * 当前登录用户的 ThreadLocal 容器。
 *
 * <p><b>必须成对使用</b>：过滤器 finally 里一定要 {@link #clear()}，
 * 否则 Tomcat 线程复用会把上一个请求的用户带到下一个请求里——
 * 这类 bug 在生产环境极难排查（表现是"偶尔串号"）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 必须登录才能继续的接口用它，未登录直接抛 20001 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String currentUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? "anonymous" : user.getUsername();
    }

    public static Long currentStoreId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getStoreId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
