package com.grindset.backend.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取客户端 IP,供登录限流等按来源统计的场景使用。
 * 优先信任反向代理追加的 X-Forwarded-For 第一段,其次 X-Real-IP,最后回落到直连地址。
 * 注意:X-Forwarded-For 客户端可伪造,只有部署在可信网关/nginx 之后时该头才有意义;
 * 直接暴露公网时应以 getRemoteAddr() 为准。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
