package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
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

import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_MESSAGE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_REQUEST_MESSAGES;

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
        String model = strip(request.getModel());
        List<LlmTarget> targets = catalog.requireTargets(strip(request.getProvider()), model);
        router.stream(targets, model, buildMessages(request.getMessages()), onDelta);
    }

    /** 校验历史消息,裁到最近若干条,并在最前面注入 system 提示词 */
    private List<ChatMessage> buildMessages(List<ChatRequest.Message> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息不能为空");
        }
        if (raw.size() > CHAT_MAX_REQUEST_MESSAGES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息数量过多");
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
                throw new BizException(ErrorCode.BAD_REQUEST, "消息角色不合法");
            }
            String content = item.getContent() == null ? "" : item.getContent();
            if (content.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
            }
            if (content.length() > CHAT_MAX_MESSAGE_LENGTH) {
                throw new BizException(ErrorCode.BAD_REQUEST, "单条消息不能超过 " + CHAT_MAX_MESSAGE_LENGTH + " 字");
            }
            result.add(new ChatMessage(role, content));
        }
        return result;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
