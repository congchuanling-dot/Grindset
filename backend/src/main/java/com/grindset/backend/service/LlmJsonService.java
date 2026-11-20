package com.grindset.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * 非流式 JSON 场景的统一入口:解析、失败重试、错误文案都收在这里。
 * 简历解析、面试决策/评分/复盘都用它,避免每条链路各写一套。
 */
@Service
public class LlmJsonService {

    private static final Logger log = LoggerFactory.getLogger(LlmJsonService.class);

    private static final String RETRY_HINT =
            "上一次输出不合法(%s)。请只输出符合要求的 JSON 对象,不要任何解释。";

    private final LlmRouter router;
    private final ObjectMapper objectMapper;

    public LlmJsonService(LlmRouter router, ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
    }

    /** 解析模型输出:去掉代码块围栏,截取第一个 { 到最后一个 },失败抛 502 */
    public <T> T parse(String raw, Class<T> type) {
        String text = raw == null ? "" : raw.strip();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            if (newline >= 0) {
                text = text.substring(newline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "模型输出不是合法 JSON");
        }
        try {
            T parsed = objectMapper.readValue(text.substring(start, end + 1), type);
            if (parsed == null) {
                throw new BizException(ErrorCode.BAD_GATEWAY, "模型输出不是合法 JSON");
            }
            return parsed;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "模型输出不是合法 JSON");
        }
    }

    /**
     * 非流式调用 + 解析,失败时带提示重试一次;仍失败抛 failureMessage。
     * parser 里可以继续做规范化与业务校验,抛 BizException 即视为失败。
     */
    public <T> T call(List<LlmTarget> targets, String model, List<ChatMessage> messages,
                      Function<String, T> parser, String failureMessage) {
        String raw = router.complete(targets, model, messages);
        try {
            return parser.apply(raw);
        } catch (BizException first) {
            log.warn("{} 结果不合法,准备重试一次,reason={}", failureMessage, first.getMessage());
            messages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, raw));
            messages.add(new ChatMessage(ChatMessage.ROLE_USER,
                    String.format(RETRY_HINT, first.getMessage())));
            String retryRaw = router.complete(targets, model, messages);
            try {
                return parser.apply(retryRaw);
            } catch (BizException e) {
                throw new BizException(ErrorCode.BAD_GATEWAY, failureMessage);
            }
        }
    }

    /** 不重试的单次调用 */
    public <T> T callOnce(List<LlmTarget> targets, String model, List<ChatMessage> messages,
                          Function<String, T> parser, String failureMessage) {
        try {
            return parser.apply(router.complete(targets, model, messages));
        } catch (BizException e) {
            throw new BizException(ErrorCode.BAD_GATEWAY, failureMessage);
        }
    }
}
