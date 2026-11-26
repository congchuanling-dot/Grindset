package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音合成配置(豆包语音合成,HTTP 单向流式)。
 * api-key 只在后端使用,浏览器只拿到自己的 access token。
 */
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(boolean enabled, String apiKey, String resourceId,
                            String speaker, String endpoint, int timeoutSeconds) {

    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
