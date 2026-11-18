package com.grindset.backend.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google OAuth 2.0 HTTP 客户端。
 * 只负责换令牌与拉用户信息,不碰数据库和 Redis。
 */
@Component
public class GoogleOAuthClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public GoogleOAuthClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 用授权码换 Google access token,凭证来自数据库配置 */
    public String exchangeCode(String code, OAuthClientConfig config) {
        String form = "code=" + encode(code)
                + "&client_id=" + encode(config.clientId())
                + "&client_secret=" + encode(config.clientSecret())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        JsonNode node = send(request, "获取 Google 令牌");
        if (node.hasNonNull("error")) {
            String detail = node.path("error_description").asText(node.path("error").asText());
            throw new BizException(ErrorCode.UNAUTHORIZED, "Google 授权失败：" + detail);
        }
        String accessToken = text(node, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Google 未返回 access_token");
        }
        return accessToken;
    }

    /** 通过 OIDC userinfo 拉取用户信息 */
    public GoogleOAuthUser fetchUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO_URL))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode node = send(request, "获取 Google 用户信息");
        String sub = node.path("sub").asText("");
        if (sub.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Google 未返回用户标识");
        }
        return new GoogleOAuthUser(sub, text(node, "name"), text(node, "email"),
                node.path("email_verified").asBoolean(false), text(node, "picture"));
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BizException(ErrorCode.BAD_GATEWAY, action + "失败（HTTP " + response.statusCode() + "）");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.BAD_GATEWAY, action + "被中断");
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.BAD_GATEWAY, action + "返回格式异常");
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "无法连接 Google，请稍后重试");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
