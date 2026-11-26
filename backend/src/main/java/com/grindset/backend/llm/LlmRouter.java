package com.grindset.backend.llm;

import com.grindset.backend.service.AiUsageService;
import com.grindset.backend.service.LlmKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
    private final AiUsageService usageService;

    public LlmRouter(LlmClient client, LlmKeyService keyService, AiUsageService usageService) {
        this.client = client;
        this.keyService = keyService;
        this.usageService = usageService;
    }

    /**
     * @param targets 候选目标,已按渠道优先级 + key 最久未用排序
     */
    public void stream(List<LlmTarget> targets, String model, List<ChatMessage> messages,
                       Consumer<String> onDelta, LlmUsageContext context) {
        LlmException last = null;
        for (LlmTarget target : targets) {
            // started 一旦为 true,说明已经给前端吐过字;此后失败不能换 key,
            // 否则新 key 会从头再生成一遍,用户会看到重复内容
            boolean[] started = {false};
            try {
                LlmUsage usage = client.streamChat(target, model, messages, text -> {
                    started[0] = true;
                    onDelta.accept(text);
                });
                keyService.markUsed(target.keyId());
                // 只有成功这次记账;失败换 key 的尝试不重复计费
                usageService.record(context, target, model, usage);
                return;
            } catch (LlmException e) {
                if (started[0]) {
                    // 已经吐字给前端了,不能无缝重试
                    throw e;
                }
                log.warn("模型调用失败,尝试下一个 key provider={} keyId={} reason={}",
                        target.providerId(), target.keyId(), e.getMessage());
                last = e;
                // 失败累计,连续失败会进入冷却;鉴权失败会被自动停用
                keyService.markFailed(target.keyId(), e.getUpstreamStatus());
            }
        }
        throw last == null ? new LlmException(502, "没有可用的模型服务") : last;
    }

    /**
     * 非流式调用:逐个候选 key 尝试,直到拿到完整结果。
     * 非流式没有"已吐字不能重试"的限制,失败可以直接换下一把 key。
     */
    public String complete(List<LlmTarget> targets, String model, List<ChatMessage> messages,
                           LlmUsageContext context) {
        return completeWithTools(targets, model, messages, List.of(), context).content();
    }

    /** 带 function calling 工具定义的非流式调用,重试策略同上 */
    public LlmCompletion completeWithTools(List<LlmTarget> targets, String model,
                                           List<ChatMessage> messages,
                                           List<Map<String, Object>> tools,
                                           LlmUsageContext context) {
        LlmException last = null;
        for (LlmTarget target : targets) {
            try {
                LlmCompletion result = client.completeWithTools(target, model, messages, tools);
                // 记录使用时间,多 key 轮询靠它把"最久未用"排到前面
                keyService.markUsed(target.keyId());
                usageService.record(context, target, model, result.usage());
                return result;
            } catch (LlmException e) {
                log.warn("模型调用失败,尝试下一个 key provider={} keyId={} reason={}",
                        target.providerId(), target.keyId(), e.getMessage());
                last = e;
                // 非流式没有"已经输出一半"的问题,失败可以直接换下一把 key
                keyService.markFailed(target.keyId(), e.getUpstreamStatus());
            }
        }
        throw last == null ? new LlmException(502, "没有可用的模型服务") : last;
    }
}
