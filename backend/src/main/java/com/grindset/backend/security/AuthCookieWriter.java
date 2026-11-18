package com.grindset.backend.security;

import com.grindset.backend.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token Cookie 的唯一写入点。
 * 登录、注册、刷新、登出、后续第三方登录都走这里,
 * 避免 path / SameSite / Secure 等属性在多处各写一份。
 */
@Component
public class AuthCookieWriter {

    /** Cookie 名,Controller 的 @CookieValue 也引用它 */
    public static final String REFRESH_COOKIE = "refresh_token";

    /** Cookie 只在认证接口上携带,减少暴露面 */
    public static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public AuthCookieWriter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /** 下发 refresh token Cookie */
    public void writeRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(token, Duration.ofDays(jwtProperties.getRefreshTtlDays())).toString());
    }

    /** 清掉浏览器里的 refresh Cookie(maxAge=0) */
    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
