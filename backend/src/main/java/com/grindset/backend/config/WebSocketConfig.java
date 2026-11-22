package com.grindset.backend.config;

import com.grindset.backend.asr.AsrWebSocketHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 语音识别 WebSocket 配置。
 * /api/asr/stream 在 SecurityConfig 放行,access token 由 Handler 自己校验。
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(AsrProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

    private final AsrWebSocketHandler asrHandler;

    public WebSocketConfig(AsrWebSocketHandler asrHandler) {
        this.asrHandler = asrHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrHandler, "/api/asr/stream").setAllowedOriginPatterns("*");
    }

    /** 一包 PCM 6400 字节,放大二进制缓冲留出余量 */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxTextMessageBufferSize(16 * 1024);
        return container;
    }
}
