package com.grindset.backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型层的运行时配置。
 * 渠道、模型、API Key 都在数据库(llm_channels / llm_models / llm_keys);
 * 这里的 apiKeys 只是旧部署的兼容兜底:库里没配 key 的厂商才会读环境变量。
 */
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** 注入到每次对话最前面的系统提示词,统一约束回答风格 */
    private String systemPrompt = "你是面试复习助手,回答简洁准确,用中文。";

    /** 最多携带多少条历史消息,防止 token 无限增长 */
    private int maxHistoryMessages = 20;

    /** provider id -> API Key,仅当库里没配 key 时兜底 */
    private Map<String, String> apiKeys = new LinkedHashMap<>();

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public Map<String, String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Map<String, String> apiKeys) {
        this.apiKeys = apiKeys;
    }
}
