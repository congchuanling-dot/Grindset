package com.grindset.backend.service.agent;

import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 用 LLM 给会话起标题,要求不超过 15 个字。
 *
 * <p>标题只是锦上添花:生成失败、超时都回退到截断后的首条提问,绝不影响主流程。
 * 调用方会把它和回答并行执行,回答结束时标题通常已经生成好了。</p>
 */
@Service
public class AgentTitleService {

    private static final Logger log = LoggerFactory.getLogger(AgentTitleService.class);

    /** 标题最大长度,与前端侧边栏的展示宽度匹配 */
    private static final int MAX_TITLE_LENGTH = 15;

    /** 标题里需要去掉的首尾符号 */
    private static final String EDGE_CHARS = "\"'“”‘’《》〈〉#*`。！？，、；：,.!?;:-—–（）()[]【】{} ";

    private final LlmRouter router;

    public AgentTitleService(LlmRouter router) {
        this.router = router;
    }

    /**
     * 异步生成标题。
     *
     * @param fallback 生成失败时使用的兜底标题(通常是截断后的首条提问)
     */
    public CompletableFuture<String> generateAsync(List<LlmTarget> targets, String model,
                                                   String question, String fallback) {
        CompletableFuture<String> future = new CompletableFuture<>();
        // 虚拟线程:不占平台线程,标题生成失败也不影响请求线程
        Thread.ofVirtual().name("agent-title-", 0).start(
                () -> future.complete(generate(targets, model, question, fallback)));
        return future;
    }

    private String generate(List<LlmTarget> targets, String model, String question, String fallback) {
        try {
            String raw = router.complete(targets, model, List.of(
                    new ChatMessage(ChatMessage.ROLE_SYSTEM, """
                            你是会话标题生成器。根据用户提问生成一个不超过 15 个字的标题。
                            只输出标题本身,不要引号、标点、编号或任何解释。
                            """),
                    new ChatMessage(ChatMessage.ROLE_USER, "用户提问:" + question)));
            String title = normalize(raw, fallback);
            log.debug("Agent 会话标题生成完成 title={}", title);
            return title;
        } catch (RuntimeException e) {
            // 标题失败不算业务错误,保留兜底标题即可
            log.warn("Agent 会话标题生成失败,使用兜底标题", e);
            return fallback;
        }
    }

    /** 去掉换行、首尾符号,并截断到 15 个字 */
    private static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String title = raw.replace('\r', ' ').replace('\n', ' ').strip();
        int start = 0;
        int end = title.length();
        while (start < end && EDGE_CHARS.indexOf(title.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && EDGE_CHARS.indexOf(title.charAt(end - 1)) >= 0) {
            end--;
        }
        title = title.substring(start, end);
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }
        return title.isBlank() ? fallback : title;
    }
}
