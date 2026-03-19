package com.bub6le.crackserver.common;

/**
 * 使用 ThreadLocal 存储当前请求的用户 ID，实现不同层之间的数据共享解耦
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    public static void clear() {
        USER_ID_THREAD_LOCAL.remove();
    }
}
