package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音识别配置。api-key 只在后端使用,浏览器只拿得到自己的 access token。
 * 时长分两层:调用方在 URL 上传 maxSeconds,服务端用 hardMaxSeconds 兜底。
 */
@ConfigurationProperties(prefix = "app.asr")
public record AsrProperties(boolean enabled, Volcengine volcengine, Session session) {

    public boolean available() {
        return enabled && volcengine != null && volcengine.apiKey() != null
                && !volcengine.apiKey().isBlank();
    }

    public record Volcengine(String endpoint, String apiKey, String resourceId,
                             int connectTimeoutSeconds) {
    }

    public record Session(int defaultMaxSeconds, int hardMaxSeconds, long maxAudioBytes) {
    }
}
