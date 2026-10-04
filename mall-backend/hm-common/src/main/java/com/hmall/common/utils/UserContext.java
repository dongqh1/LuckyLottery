package com.hmall.common.utils;

import com.dongqh.hmsecurity.context.TrustedUserContext;

public class UserContext {

    /**
     * 保存当前登录用户信息到ThreadLocal
     * @param userId 用户id
     */
    public static void setUser(Long userId) {
        TrustedUserContext.setUserId(userId);
    }

    /**
     * 获取当前登录用户信息
     * @return 用户id
     */
    public static Long getUser() {
        return TrustedUserContext.getUserId();
    }

    /**
     * 移除当前登录用户信息
     */
    public static void removeUser(){
        TrustedUserContext.clear();
    }
}
