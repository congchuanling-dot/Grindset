package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.dto.ProviderInfo;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对话业务:从模型目录里挑候选渠道和 key、校验参数、拼历史,再交给 LlmRouter 调度。
 */
@Service
public class ChatService {

    /** 单条消息最大字符数,挡住超长粘贴导致的巨额 token 消耗 */
    private static final int MAX_MESSAGE_LENGTH = 8000;

    /** 前端一次最多能带多少条历史,超过直接拒,避免被刷 */
    private static final int MAX_REQUEST_MESSAGES = 200;

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
        String model = strip(request.getModel());
        router.stream(resolveTargets(strip(request.getProvider()), model), model,
                buildMessages(request.getMessages()), onDelta);
    }

    /**
     * 解析候选 (渠道, key) 组合,对话和 Agent 共用。
     * 参数不合法抛 BizException(400)。
     */
    public List<LlmTarget> resolveTargets(String providerId, String model) {
        List<ModelCatalogService.CatalogChannel> channels = catalog.findChannels(providerId);
        if (channels.isEmpty()) {
            throw new BizException(400, "不支持的模型服务:" + providerId);
        }

        List<LlmTarget> targets = new ArrayList<>();
        for (ModelCatalogService.CatalogChannel channel : channels) {
            if (!channel.supports(model)) {
                continue;
            }
            for (ModelCatalogService.KeyEntry key : channel.keys()) {
                targets.add(new LlmTarget(providerId, channel.displayName(),
                        channel.baseUrl(), key.apiKey(), key.id()));
            }
        }
        if (targets.isEmpty()) {
            throw new BizException(400, "不支持的模型:" + model);
        }
        return targets;
    }

    /** 校验历史消息,裁到最近若干条,并在最前面注入 system 提示词 */
    private List<ChatMessage> buildMessages(List<ChatRequest.Message> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(400, "消息不能为空");
        }
        if (raw.size() > MAX_REQUEST_MESSAGES) {
            throw new BizException(400, "消息数量过多");
        }

        List<ChatMessage> result = new ArrayList<>();
        String systemPrompt = properties.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt));
        }

        int maxHistory = Math.max(1, properties.getMaxHistoryMessages());
        int from = Math.max(0, raw.size() - maxHistory);
        for (int i = from; i < raw.size(); i++) {
            ChatRequest.Message item = raw.get(i);
            String role = strip(item.getRole());
            if (!ChatMessage.ROLE_USER.equals(role) && !ChatMessage.ROLE_ASSISTANT.equals(role)) {
                throw new BizException(400, "消息角色不合法");
            }
            String content = item.getContent() == null ? "" : item.getContent();
            if (content.isBlank()) {
                throw new BizException(400, "消息内容不能为空");
            }
            if (content.length() > MAX_MESSAGE_LENGTH) {
                throw new BizException(400, "单条消息不能超过 " + MAX_MESSAGE_LENGTH + " 字");
            }
            result.add(new ChatMessage(role, content));
        }
        return result;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
