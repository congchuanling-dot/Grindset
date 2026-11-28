package com.grindset.backend.security;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;

/**
 * 从 {@link LoginUser} 取当前用户 ID 的统一入口。
 * 安全配置已要求登录,这里只做兜底,避免各 Controller 重复写 null 判断。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long requireId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
