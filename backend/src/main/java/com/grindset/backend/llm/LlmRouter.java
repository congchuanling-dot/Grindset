package com.grindset.backend.llm;

import com.grindset.backend.service.LlmKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 调用路由:依次尝试候选 (渠道, key) 组合。
 * 只有"还没吐出第一个字"时才允许换 key —— 流式一旦开始输出,重试会导致内容重复,
 * 所以此后遇到错误直接上报。
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final LlmClient client;
    private final LlmKeyService keyService;

    public LlmRouter(LlmClient client, LlmKeyService keyService) {
        this.client = client;
        this.keyService = keyService;
    }

    /**
     * @param targets 候选目标,已按渠道优先级 + key 最久未用排序
     */
    public void stream(List<LlmTarget> targets, String model, List<ChatMessage> messages,
                       Consumer<String> onDelta) {
        LlmException last = null;
        for (LlmTarget target : targets) {
            boolean[] started = {false};
            try {
                client.streamChat(target, model, messages, text -> {
                    started[0] = true;
                    onDelta.accept(text);
                });
                keyService.markUsed(target.keyId());
                return;
            } catch (LlmException e) {
                if (started[0]) {
                    // 已经吐字给前端了,不能无缝重试
                    throw e;
                }
                log.warn("模型调用失败,尝试下一个 key provider={} keyId={} reason={}",
                        target.providerId(), target.keyId(), e.getMessage());
                last = e;
                keyService.markFailed(target.keyId(), e.getUpstreamStatus());
            }
        }
        throw last == null ? new LlmException(502, "没有可用的模型服务") : last;
    }
}
