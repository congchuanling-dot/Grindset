package com.grindset.backend.common.constant;

/**
 * 用户账号状态,对应 users.status 列。
 */
public final class UserStatus {

    /** 正常,可登录 */
    public static final int ACTIVE = 0;

    /** 已禁用,登录与鉴权都会被拒绝 */
    public static final int DISABLED = 1;

    private UserStatus() {
    }
}
