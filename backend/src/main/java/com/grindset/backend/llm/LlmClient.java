package com.grindset.backend.llm;

import java.util.List;
import java.util.Map;
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
     * @return 本次调用的 token 用量;上游不返回 usage 时是估算值
     */
    LlmUsage streamChat(LlmTarget target, String model, List<ChatMessage> messages,
                        Consumer<String> onDelta);

    /**
     * 非流式调用,附带 function calling 的工具定义。
     * 返回正文和工具调用,两者至少有一个非空。
     *
     * <p>工具调用不走 streamChat:决策轮必须拿到完整的 tool_calls 才能执行,
     * 而流式一旦开始吐字就不能回头调工具,所以这里只提供非流式版本。</p>
     *
     * @param tools OpenAI 协议的 tools 数组,为空表示不启用工具
     */
    LlmCompletion completeWithTools(LlmTarget target, String model, List<ChatMessage> messages,
                                    List<Map<String, Object>> tools);
}
