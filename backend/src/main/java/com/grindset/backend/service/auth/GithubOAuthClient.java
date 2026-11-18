package com.grindset.backend.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.config.GithubOAuthProperties;
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
 * GitHub OAuth HTTP 客户端。
 * 只负责调 GitHub 接口并解析结果,不碰数据库和 Redis。
 */
@Component
public class GithubOAuthClient {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";
    private static final String USER_AGENT = "grindset";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final GithubOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public GithubOAuthClient(GithubOAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 用授权码换 GitHub access token */
    public String exchangeCode(String code) {
        String form = "client_id=" + encode(properties.getClientId())
                + "&client_secret=" + encode(properties.getClientSecret())
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(properties.getRedirectUri());
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        JsonNode node = send(request, "获取 GitHub 令牌");
        if (node.hasNonNull("error")) {
            String detail = node.path("error_description").asText(node.path("error").asText());
            throw new BizException(401, "GitHub 授权失败：" + detail);
        }
        String accessToken = text(node, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException(401, "GitHub 未返回 access_token");
        }
        return accessToken;
    }

    /** 拉 GitHub 用户信息;邮箱为空时再查 /user/emails 取已验证邮箱 */
    public GithubOAuthUser fetchUser(String accessToken) {
        JsonNode node = send(get(USER_URL, accessToken), "获取 GitHub 用户信息");
        String githubId = node.path("id").asText("");
        if (githubId.isBlank()) {
            throw new BizException(401, "GitHub 未返回用户 id");
        }
        String email = text(node, "email");
        if (email == null || email.isBlank()) {
            email = fetchPrimaryEmail(accessToken);
        }
        return new GithubOAuthUser(githubId, text(node, "login"),
                text(node, "name"), email, text(node, "avatar_url"));
    }

    /** 取 primary + verified 的邮箱;没有 primary 就退回第一个已验证邮箱 */
    private String fetchPrimaryEmail(String accessToken) {
        JsonNode emails = send(get(EMAILS_URL, accessToken), "获取 GitHub 邮箱");
        String fallback = null;
        for (JsonNode item : emails) {
            if (!item.path("verified").asBoolean()) {
                continue;
            }
            String address = text(item, "email");
            if (address == null || address.isBlank()) {
                continue;
            }
            if (item.path("primary").asBoolean()) {
                return address;
            }
            if (fallback == null) {
                fallback = address;
            }
        }
        return fallback;
    }

    private HttpRequest get(String url, String accessToken) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BizException(502, action + "失败（HTTP " + response.statusCode() + "）");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(502, action + "被中断");
        } catch (JsonProcessingException e) {
            throw new BizException(502, action + "返回格式异常");
        } catch (IOException e) {
            throw new BizException(502, "无法连接 GitHub，请稍后重试");
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
