package com.grindset.backend.common.constant;

/**
 * 第三方登录客户端状态,对应 oauth_client_config.status 列。
 */
public final class OAuthClientStatus {

    /** 启用 */
    public static final int ENABLED = 0;

    /** 停用 */
    public static final int DISABLED = 1;

    private OAuthClientStatus() {
    }
}
