package com.grindset.backend.llm;

/**
 * 一次模型调用的目标:用哪个渠道、哪把 key、打哪个地址。
 * 渠道信息来自数据库,key 是解密后的明文(只在内存里,不落日志)。
 */
public record LlmTarget(String providerId, String displayName, String baseUrl,
                        String apiKey, Long keyId) {
}
