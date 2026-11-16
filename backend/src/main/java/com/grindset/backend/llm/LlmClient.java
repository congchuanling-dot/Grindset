package com.grindset.backend.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 模型调用抽象。上层只依赖这个接口,换厂商 / 换协议时换实现即可。
 */
public interface LlmClient {

    /**
     * 流式对话。每收到一小段文本就回调 onDelta,调用方负责拼装和下发。
     *
     * @param target   目标渠道(地址 + 密钥 + 展示名)
     * @param model    模型 ID
     * @param messages 已含 system 提示词的完整消息列表
     * @param onDelta  增量文本回调,可能被调用很多次
     */
    void streamChat(LlmTarget target, String model, List<ChatMessage> messages,
                    Consumer<String> onDelta);

    /**
     * 非流式调用,一次性返回完整文本;供简历结构化解析这类"要完整 JSON"的场景使用。
     *
     * @return 模型输出的完整文本
     */
    String complete(LlmTarget target, String model, List<ChatMessage> messages);
}
