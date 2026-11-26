package com.grindset.backend.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.AiBizType;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmCompletion;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import com.grindset.backend.llm.LlmToolCall;
import com.grindset.backend.llm.LlmUsageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.service.agent.AgentConstants.MAX_CONTEXT_CHARS;

/**
 * Agent 主循环:模型通过 function calling 自主决定调工具还是回答,工具结果回填后继续循环。
 *
 * <p>为什么是两段调用:决策轮必须非流式,因为只有拿到完整的 tool_calls 才能执行工具,
 * 而流式一旦开始吐字就不能回头调工具;模型不再调工具时,再用一次流式调用生成正文。</p>
 *
 * <p>硬性约束:每个 tool_call_id 都必须有一条对应的 tool 消息,否则下一轮请求会被上游拒绝。</p>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmRouter router;
    private final AgentToolRegistry registry;
    private final ObjectMapper objectMapper;

    public AgentService(LlmRouter router, AgentToolRegistry registry, ObjectMapper objectMapper) {
        this.router = router;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 跑一整轮 Agent 交互,过程中通过 sink 下发事件。
     *
     * <p>历史消息由调用方从数据库重放好(最后一条必须是本次用户提问),
     * 这里只负责拼 system、调模型、执行工具,并返回本轮新增的消息流用于落库。</p>
     *
     * <p>工具执行失败不会中断循环:失败结果同样作为 tool 消息回填,由模型自己决定换参数还是收尾。
     * 客户端中途断开属于正常收尾,不抛异常,返回 aborted=true 的半成品。</p>
     *
     * @param targets 已按渠道优先级 + key 最久未用排序的候选
     * @param history 不含 system 的历史消息,最后一条是本次用户提问
     */
    public Turn stream(long userId, List<LlmTarget> targets, String model,
                       List<ChatMessage> history, AgentStreamSink sink) {
        // 用 Agent 自己的提示词,不注入首页对话的 app.llm.system-prompt
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, decisionPrompt()));
        messages.addAll(history);

        // 工具 schema 走 function calling 的 tools 参数,不拼进提示词,避免和实际实现漂移
        List<Map<String, Object>> tools = registry.toolDefinitions();
        // 决策轮和回答轮都记到同一个业务场景下
        LlmUsageContext usageContext =
                new LlmUsageContext(userId, AiBizType.AGENT_CHAT, null);

        // 只记录本轮新增的消息(system 和旧历史不落库)
        List<ChatMessage> turnMessages = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        try {
            sink.status("正在分析你的问题…");
            // 循环只由模型是否还调工具决定:没有 tool_calls 就收敛去生成回答
            while (true) {
                // 每轮都先裁上下文:工具结果会不断追加,不裁迟早超过模型窗口
                trimContext(messages);
                // 决策轮必须非流式:只有拿到完整的 tool_calls 才能决定执行哪些工具
                LlmCompletion completion = router.completeWithTools(targets, model, messages, tools, usageContext);
                if (!completion.hasToolCalls()) {
                    // 没有工具调用 = 模型认为信息够了。决策轮的正文不完整、也不该带回答格式,
                    // 直接丢弃,统一交给下面的流式回答
                    break;
                }

                List<LlmToolCall> calls = completion.toolCalls();
                // 必须原样保留模型这一轮的 tool_calls(含 id),下面的 tool 消息才能按 id 对上;
                // 漏掉任何一个 id,下一轮请求都会被上游判为非法消息序列
                ChatMessage toolCallMessage = ChatMessage.assistantToolCalls(calls);
                messages.add(toolCallMessage);
                turnMessages.add(toolCallMessage);

                // 每个失败分支都必须补一条 tool 消息:协议要求每个 tool_call_id 都有结果,
                // 不能因为参数坏了 / 工具不存在就静默跳过
                for (LlmToolCall call : calls) {
                    String argsText = call.arguments() == null || call.arguments().isBlank()
                            ? "{}" : call.arguments().strip();
                    Map<String, Object> args = parseArgs(argsText);
                    if (args == null) {
                        ChatMessage failed = ChatMessage.toolResult(call.id(), "参数不是合法 JSON,请检查后重试");
                        messages.add(failed);
                        turnMessages.add(failed);
                        continue;
                    }
                    AgentTool tool = registry.find(call.name());
                    if (tool == null) {
                        ChatMessage failed = ChatMessage.toolResult(call.id(), "工具 " + call.name() + " 不存在");
                        messages.add(failed);
                        turnMessages.add(failed);
                        continue;
                    }

                    log.info("Agent 调用工具 userId={} tool={} args={}", userId, tool.name(), args);
                    sink.toolCall(call.id(), tool.name(), tool.label(), args);

                    // 工具异常不往外抛:转成失败结果回填,让模型自己决定换参数、换工具还是直接收尾
                    AgentToolResult result;
                    try {
                        result = tool.execute(userId, args);
                    } catch (RuntimeException e) {
                        log.warn("Agent 工具执行失败 userId={} tool={}", userId, tool.name(), e);
                        result = new AgentToolResult(false, "查询失败",
                                Map.of("error", "工具执行异常,请如实告知用户暂时查不到"));
                    }
                    sink.toolResult(call.id(), tool.name(), result.ok(), result.summary(), result.data());
                    ChatMessage toolMessage = ChatMessage.toolResult(call.id(), toJson(result.data()));
                    messages.add(toolMessage);
                    turnMessages.add(toolMessage);
                }
                sink.status("正在整理查询结果…");
            }

            sink.status("正在生成回答…");
            // 复用同一份消息(含真实工具结果),只把最前面的 system 换成回答提示词;
            // 这样模型能看到查询结果,又不会被"继续调工具"的规则干扰
            messages.set(0, new ChatMessage(ChatMessage.ROLE_SYSTEM, answerPrompt()));
            trimContext(messages);
            router.stream(targets, model, messages, text -> {
                answer.append(text);
                sink.delta(text);
            }, usageContext);
        } catch (StreamAbortedException e) {
            // 客户端断开:已经生成的正文和已执行的工具都要带给调用方落库
            turnMessages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, answer.toString()));
            return new Turn(turnMessages, true);
        }

        turnMessages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, answer.toString()));
        return new Turn(turnMessages, false);
    }

    /** 决策轮提示词:人设 + 使用规则 + 画像。工具 schema 不写在这里,由 tools 参数提供 */
    private String decisionPrompt() {
        StringBuilder prompt = new StringBuilder("""
                你是 Grindset 的面试复习助手,可以调用工具查询当前用户的真实学习数据。
                禁止编造数据;查不到就如实说明。

                使用规则:
                1. 需要用户自己的学习数据时,调用对应工具,不要凭记忆猜测。
                2. 一次可以调用多个工具;不要用相同参数重复查询。
                3. 信息足够就直接回答,不要再调用工具。
                4. 纯闲聊或通用概念直接回答。
                """);
        // 用户画像暂未接入,为空时整段跳过
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
                5. 工具结果已经用卡片列出选项时,正文只用一两句话说明现状并引导用户选择,
                   不要重复罗列清单内容。
                """;
    }

    /**
     * 上下文超长时按完整 turn 整组丢弃。
     *
     * <p>一个 turn = assistant(tool_calls) + 紧随其后的所有 tool 消息。必须整组丢:
     * 只丢半组会留下没有结果的 tool_call_id,上游直接 400。
     * 普通 user / assistant 每条算一组;保留最近 3 组,且永远不丢最后一条用户提问。</p>
     */
    private void trimContext(List<ChatMessage> messages) {
        if (messages.size() <= 4 || totalChars(messages) <= MAX_CONTEXT_CHARS) {
            return;
        }

        // 从 system 之后开始切组,[start, end) 记录原始下标,后面按组删除
        List<int[]> groups = new ArrayList<>();
        int index = 1;
        while (index < messages.size()) {
            int start = index;
            ChatMessage message = messages.get(index);
            index++;
            if (ChatMessage.ROLE_ASSISTANT.equals(message.role())
                    && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                // assistant 带 tool_calls 时,把后面连续的 tool 消息一起并进这一组
                while (index < messages.size() && ChatMessage.ROLE_TOOL.equals(messages.get(index).role())) {
                    index++;
                }
            }
            groups.add(new int[]{start, index});
        }
        if (groups.size() <= 3) {
            return;
        }

        // 最后一条用户消息就是本次提问,任何情况下都要留着,否则模型不知道在回答什么
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 1; i--) {
            if (ChatMessage.ROLE_USER.equals(messages.get(i).role())) {
                lastUser = i;
                break;
            }
        }

        // 从最老的组开始丢,字符数降到阈值以下就停;最后 3 组永远不参与丢弃
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

    /** 粗略按字符数估算上下文长度,不引 tokenizer,够用且零依赖 */
    private static int totalChars(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += message.content() == null ? 0 : message.content().length();
        }
        return total;
    }

    /** 模型给的 arguments 是 JSON 字符串,可能带脏数据;解析失败返回 null,由调用方回错误 tool 消息 */
    private Map<String, Object> parseArgs(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Agent 工具参数解析失败 args={}", text);
            return null;
        }
    }

    /** 工具数据序列化成 tool 消息内容;序列化失败也要给个字符串,不能让整轮对话挂掉 */
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

    /**
     * 一轮 Agent 交互产出的消息流(不含 system,可直接落库)。
     * aborted=true 表示客户端中途断开,消息流可能只到工具调用、最终回答也可能是半截。
     */
    public record Turn(List<ChatMessage> messages, boolean aborted) {
    }

    /** 客户端主动断开(点停止、关页面、切换会话)时抛出,和业务失败区分开 */
    public static class StreamAbortedException extends RuntimeException {
    }
}
