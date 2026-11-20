package com.grindset.backend.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmCompletion;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import com.grindset.backend.llm.LlmToolCall;
import com.grindset.backend.service.ChatHistoryBuilder;
import com.grindset.backend.service.ModelCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.grindset.backend.service.agent.AgentConstants.MAX_CONTEXT_CHARS;
import static com.grindset.backend.service.agent.AgentConstants.MAX_ROUNDS;
import static com.grindset.backend.service.agent.AgentConstants.MAX_RUN_MILLIS;
import static com.grindset.backend.service.agent.AgentConstants.MAX_TOOL_CALLS_PER_ROUND;
import static com.grindset.backend.service.agent.AgentConstants.MAX_TOTAL_TOOL_CALLS;

/**
 * Agent 主循环:模型通过 function calling 自主决定调工具还是回答,
 * 工具结果回填后继续循环;模型不再调用工具时,再用一次流式调用生成正文。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmProperties properties;
    private final ModelCatalogService catalog;
    private final LlmRouter router;
    private final AgentToolRegistry registry;
    private final ObjectMapper objectMapper;

    public AgentService(LlmProperties properties, ModelCatalogService catalog, LlmRouter router,
                        AgentToolRegistry registry, ObjectMapper objectMapper) {
        this.properties = properties;
        this.catalog = catalog;
        this.router = router;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 跑一整轮 Agent 交互,过程中通过 sink 下发事件。
     * 参数问题抛 BizException(400);上游模型问题抛 LlmException,由 Controller 转成 error 事件。
     */
    public void stream(long userId, ChatRequest request, AgentStreamSink sink) {
        String providerId = Texts.strip(request.getProvider());
        String model = Texts.strip(request.getModel());
        List<LlmTarget> targets = catalog.requireTargets(providerId, model);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, decisionPrompt()));
        messages.addAll(ChatHistoryBuilder.build(request.getMessages(),
                properties.getMaxHistoryMessages(), null, true));

        List<Map<String, Object>> tools = registry.toolDefinitions();
        Set<String> seenCalls = new HashSet<>();
        long deadline = System.currentTimeMillis() + MAX_RUN_MILLIS;
        int totalCalls = 0;
        int round = 0;

        while (true) {
            if (round >= MAX_ROUNDS || System.currentTimeMillis() > deadline) {
                break;
            }
            round++;
            sink.status(round == 1 ? "正在分析你的问题…" : "正在整理查询结果…");

            trimContext(messages);
            LlmCompletion completion = router.completeWithTools(targets, model, messages, tools);
            if (!completion.hasToolCalls()) {
                // 决策轮的正文直接丢弃,统一交给下面的流式回答
                break;
            }

            List<LlmToolCall> calls = completion.toolCalls();
            messages.add(ChatMessage.assistantToolCalls(calls));

            int callsThisRound = 0;
            for (LlmToolCall call : calls) {
                String argsText = call.arguments() == null || call.arguments().isBlank()
                        ? "{}" : call.arguments().strip();
                Map<String, Object> args = parseArgs(argsText);
                if (args == null) {
                    messages.add(ChatMessage.toolResult(call.id(), "参数不是合法 JSON,请检查后重试"));
                    continue;
                }
                AgentTool tool = registry.find(call.name());
                if (tool == null) {
                    messages.add(ChatMessage.toolResult(call.id(), "工具 " + call.name() + " 不存在"));
                    continue;
                }
                if (!seenCalls.add(call.name() + "|" + argsText)) {
                    messages.add(ChatMessage.toolResult(call.id(), "相同参数已经查询过,不要重复调用"));
                    continue;
                }
                if (callsThisRound >= MAX_TOOL_CALLS_PER_ROUND || totalCalls >= MAX_TOTAL_TOOL_CALLS) {
                    messages.add(ChatMessage.toolResult(call.id(), "工具调用次数已达上限,请直接根据已有信息回答"));
                    continue;
                }
                callsThisRound++;
                totalCalls++;

                log.info("Agent 调用工具 userId={} tool={} args={}", userId, tool.name(), args);
                sink.toolCall(call.id(), tool.name(), tool.label(), args);

                AgentToolResult result;
                try {
                    result = tool.execute(userId, args);
                } catch (RuntimeException e) {
                    log.warn("Agent 工具执行失败 userId={} tool={}", userId, tool.name(), e);
                    result = new AgentToolResult(false, "查询失败",
                            Map.of("error", "工具执行异常,请如实告知用户暂时查不到"));
                }
                sink.toolResult(call.id(), tool.name(), result.ok(), result.summary());
                messages.add(ChatMessage.toolResult(call.id(), toJson(result.data())));
            }
        }

        sink.status("正在生成回答…");
        messages.set(0, new ChatMessage(ChatMessage.ROLE_SYSTEM, answerPrompt()));
        trimContext(messages);
        router.stream(targets, model, messages, sink::delta);
    }

    /** 决策轮提示词:人设 + 使用规则 + 用户画像(暂未接入) */
    private String decisionPrompt() {
        StringBuilder prompt = new StringBuilder("""
                你是 Grindset 的面试复习助手,可以调用工具查询当前用户的真实学习数据。
                禁止编造数据;查不到就如实说明。

                使用规则:
                1. 需要用户自己的学习数据时,调用对应工具,不要凭记忆猜测。
                2. 一次可以调用多个工具;不要用相同参数重复查询。
                3. 信息足够就直接回答,不要再调用工具。
                4. 纯闲聊或通用概念直接回答。
                5. 需要站外事实(最新版本、官方文档、报错原因、技术方案)时,先 web_search;
                   搜到链接后如需细读,再用 fetch_url 读一两条最相关的页面。
                """);
        prompt.append("6. 最多 ").append(MAX_ROUNDS).append(" 轮工具调用。\n");

        String profile = profileSection();
        if (!profile.isEmpty()) {
            prompt.append('\n').append(profile);
        }
        return prompt.toString();
    }

    /** 用户画像暂未接入,为空时整段跳过 */
    private String profileSection() {
        return "";
    }

    /** 最终回答提示词:只基于已有数据说人话,不再调用工具 */
    private String answerPrompt() {
        return """
                你是 Grindset 的面试复习助手。请基于上面的对话和工具查询结果回答。
                要求:
                1. 只用已有数据,禁止编造数字、题目或记录;数据不足就直说。
                2. 中文、先给结论再给依据,建议要具体可执行。
                3. 不要提"工具""接口"这类实现细节。
                4. 不要用 Markdown 标题(#)和加粗(**),用短段落和"- "列表表达层次。
                5. 引用网页资料要用自己的话概括,并在末尾用"- 参考:<链接>"列出实际用到的链接,
                   不要整段抄录,也不要虚构没出现过的链接。
                6. 区分"你的学习数据"和"网上资料",不要混为一谈。
                """;
    }

    /**
     * 上下文超长时按完整 turn 整组丢弃:助手工具调用 + 后续工具结果算一组,
     * 普通消息每条算一组。保留最后 3 组和最后一条用户消息。
     */
    private void trimContext(List<ChatMessage> messages) {
        if (messages.size() <= 4 || totalChars(messages) <= MAX_CONTEXT_CHARS) {
            return;
        }

        List<int[]> groups = new ArrayList<>();
        int index = 1;
        while (index < messages.size()) {
            int start = index;
            ChatMessage message = messages.get(index);
            index++;
            if (ChatMessage.ROLE_ASSISTANT.equals(message.role())
                    && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                while (index < messages.size() && ChatMessage.ROLE_TOOL.equals(messages.get(index).role())) {
                    index++;
                }
            }
            groups.add(new int[]{start, index});
        }
        if (groups.size() <= 3) {
            return;
        }

        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 1; i--) {
            if (ChatMessage.ROLE_USER.equals(messages.get(i).role())) {
                lastUser = i;
                break;
            }
        }

        List<int[]> removed = new ArrayList<>();
        int chars = totalChars(messages);
        for (int g = 0; g < groups.size() - 3 && chars > MAX_CONTEXT_CHARS; g++) {
            int[] group = groups.get(g);
            if (lastUser >= group[0] && lastUser < group[1]) {
                continue;
            }
            removed.add(group);
            chars -= totalChars(messages.subList(group[0], group[1]));
        }
        // 从后往前删,避免前面的下标失效
        for (int i = removed.size() - 1; i >= 0; i--) {
            int[] group = removed.get(i);
            messages.subList(group[0], group[1]).clear();
        }
        // 丢弃后开头如果是助手发言,一并去掉,保证首条非 system 是用户
        while (messages.size() > 1 && ChatMessage.ROLE_ASSISTANT.equals(messages.get(1).role())) {
            messages.remove(1);
        }
    }

    private static int totalChars(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += message.content() == null ? 0 : message.content().length();
        }
        return total;
    }

    private Map<String, Object> parseArgs(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Agent 工具参数解析失败 args={}", text);
            return null;
        }
    }

    private String toJson(Object data) {
        if (data == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data);
        }
    }
}
