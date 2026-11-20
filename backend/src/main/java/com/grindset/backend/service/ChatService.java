package com.grindset.backend.service;

import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.dto.ProviderInfo;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 对话业务:从模型目录里挑候选渠道和 key、校验参数、拼历史,再交给 LlmRouter 调度。
 */
@Service
public class ChatService {

    private final LlmProperties properties;
    private final ModelCatalogService catalog;
    private final LlmRouter router;

    public ChatService(LlmProperties properties, ModelCatalogService catalog, LlmRouter router) {
        this.properties = properties;
        this.catalog = catalog;
        this.router = router;
    }

    /** 有可用配置的厂商列表,供前端渲染下拉框 */
    public List<ProviderInfo> listAvailableProviders() {
        return catalog.listProviders();
    }

    /**
     * 校验并开始流式对话,每个增量片段回调 onDelta。
     * 参数问题抛 BizException(400),上游问题由 LlmRouter 抛 LlmException。
     */
    public void stream(ChatRequest request, Consumer<String> onDelta) {
        String model = Texts.strip(request.getModel());
        List<LlmTarget> targets = catalog.requireTargets(Texts.strip(request.getProvider()), model);
        List<ChatMessage> messages = ChatHistoryBuilder.build(request.getMessages(),
                properties.getMaxHistoryMessages(), properties.getSystemPrompt(), false);
        router.stream(targets, model, messages, onDelta);
    }
}
