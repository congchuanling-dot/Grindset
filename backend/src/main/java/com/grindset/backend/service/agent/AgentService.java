package com.grindset.backend.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.LlmRouter;
import com.grindset.backend.llm.LlmTarget;
import com.grindset.backend.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_MESSAGE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_REQUEST_MESSAGES;
import static com.grindset.backend.service.agent.AgentConstants.MAX_OBSERVATION_CHARS;
import static com.grindset.backend.service.agent.AgentConstants.MAX_STEPS;

/**
 * Agent 主循环:模型决策 -> 执行只读工具 -> 观察结果回填,直到 final 后用真实流式输出正文。
 * 决策和最终回答是两次模型调用:决策轮只出 JSON,避免正文和工具调用混在一起。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmProperties properties;
    private final ChatService chatService;
    private final LlmRouter router;
    private final AgentToolRegistry registry;
    private final ObjectMapper objectMapper;

    public AgentService(LlmProperties properties, ChatService chatService, LlmRouter router,
                        AgentToolRegistry registry, ObjectMapper objectMapper) {
        this.properties = properties;
        this.chatService = chatService;
        this.router = router;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 跑一整轮 Agent 交互,过程中通过 sink 下发事件。
     * 参数问题抛 BizException(400);上游模型问题抛 LlmException,由 Controller 转成 error 事件。
     */
    public void stream(long userId, ChatRequest request, AgentStreamSink sink) {
        String providerId = strip(request.getProvider());
        String model = strip(request.getModel());
        List<LlmTarget> targets = chatService.resolveTargets(providerId, model);
        List<ChatMessage> history = buildHistory(request.getMessages());

        List<ChatMessage> decisionContext = new ArrayList<>();
        decisionContext.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, decisionPrompt()));
        decisionContext.addAll(history);

        List<Observation> observations = new ArrayList<>();
        for (int step = 0; step < MAX_STEPS; step++) {
            sink.status(step == 0 ? "正在分析你的问题…" : "正在整理查询结果…");
            String raw = router.complete(targets, model, decisionContext);
            Decision decision = parseDecision(raw);
            if (decision == null) {
                // 解析不出来或模型选择直接回答:收束循环,交给下面的流式回答
                break;
            }

            AgentTool tool = registry.find(decision.tool());
            if (tool == null) {
                decisionContext.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, raw));
                decisionContext.add(new ChatMessage(ChatMessage.ROLE_USER,
                        "工具 " + decision.tool() + " 不存在,请从可用工具中选择,或直接返回 final。"));
                continue;
            }

            String callId = "t" + (step + 1);
            log.info("Agent 调用工具 userId={} tool={} args={}", userId, tool.name(), decision.args());
            sink.toolCall(callId, tool.name(), tool.label(), decision.args());

            AgentToolResult result;
            try {
                result = tool.execute(userId, decision.args());
            } catch (RuntimeException e) {
                log.warn("Agent 工具执行失败 userId={} tool={}", userId, tool.name(), e);
                result = new AgentToolResult(false, "查询失败",
                        Map.of("error", "工具执行异常,请如实告知用户暂时查不到"));
            }
            sink.toolResult(callId, tool.name(), result.ok(), result.summary());

            String payload = truncate(toJson(result.data()));
            observations.add(new Observation(tool.name(), result.summary(), payload));
            decisionContext.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, raw));
            decisionContext.add(new ChatMessage(ChatMessage.ROLE_USER,
                    "工具 " + tool.name() + " 的执行结果(" + result.summary() + "):\n" + payload
                            + "\n如果信息已经足够,请返回 final。"));
        }

        sink.status("正在生成回答…");
        router.stream(targets, model, answerMessages(history, observations), sink::delta);
    }

    /** 决策轮提示词:工具清单 + 严格 JSON 协议 */
    private String decisionPrompt() {
        StringBuilder sb = new StringBuilder("""
                你是 Grindset 的面试复习助手,可以通过只读工具查询当前用户的真实学习数据。
                禁止编造数据;查不到就如实说明。

                可用工具:
                """);
        for (AgentTool tool : registry.all()) {
            sb.append("- ").append(tool.name()).append(":").append(tool.description()).append('\n');
        }
        sb.append("""

                输出协议:只输出一个 JSON 对象,不要 Markdown 代码块,不要输出多余文字。
                调用工具:{"thought":"一句话说明","action":"tool","tool":"工具名","args":{}}
                直接回答:{"thought":"一句话说明","action":"final"}

                规则:
                1. 每轮只能调用一个工具,不要用相同参数重复查询。
                2. 信息足够就返回 final,最终回答由系统另行生成。
                3. 纯闲聊或通用概念可以直接返回 final。
                4. 需要站外事实(最新版本、官方文档、报错原因、技术方案)时,先 web_search;
                   搜到链接后如需细读,再用 fetch_url 读一两条最相关的页面。
                5. 最多 5 轮。
                """);
        return sb.toString();
    }

    /** 最终回答提示词:只基于工具数据说人话,不再输出 JSON */
    private String answerPrompt() {
        return """
                你是 Grindset 的面试复习助手。请基于下面提供的用户真实学习数据回答。
                要求:
                1. 只用已给数据,禁止编造数字、题目或记录;数据不足就直说。
                2. 中文、先给结论再给依据,建议要具体可执行。
                3. 不要输出 JSON,不要提"工具""接口"这类实现细节。
                4. 不要用 Markdown 标题(#)和加粗(**),用短段落和"- "列表表达层次。
                5. 如果引用了网页资料,要用自己的话概括,并在回答末尾用"- 参考:<链接>"列出实际用到的链接;
                   不要把网页原文整段抄过来,也不要虚构没出现过的链接。
                6. 区分"你的学习数据"和"网上资料",不要混为一谈。
                """;
    }

    /**
     * 拼最终回答的上下文:历史里最后一条是本次提问,把工具观察拼进这条 user 消息,
     * 保证 user/assistant 交替,兼容对消息顺序敏感的厂商。
     */
    private List<ChatMessage> answerMessages(List<ChatMessage> history, List<Observation> observations) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, answerPrompt()));
        if (observations.isEmpty()) {
            messages.addAll(history);
            return messages;
        }

        int last = history.size() - 1;
        messages.addAll(history.subList(0, last));

        StringBuilder question = new StringBuilder(history.get(last).content());
        question.append("\n\n系统查询到以下数据:\n");
        for (Observation observation : observations) {
            question.append("【").append(observation.name()).append("】")
                    .append(observation.summary()).append('\n')
                    .append(observation.data()).append('\n');
        }
        question.append("\n请结合以上数据回答上面的问题。");
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, question.toString()));
        return messages;
    }

    /** 校验历史消息,裁到最近若干条;不注入 app.llm 的通用 system 提示词 */
    private List<ChatMessage> buildHistory(List<ChatRequest.Message> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(400, "消息不能为空");
        }
        if (raw.size() > CHAT_MAX_REQUEST_MESSAGES) {
            throw new BizException(400, "消息数量过多");
        }

        List<ChatMessage> result = new ArrayList<>();
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
            if (content.length() > CHAT_MAX_MESSAGE_LENGTH) {
                throw new BizException(400, "单条消息不能超过 " + CHAT_MAX_MESSAGE_LENGTH + " 字");
            }
            result.add(new ChatMessage(role, content));
        }
        if (!ChatMessage.ROLE_USER.equals(result.get(result.size() - 1).role())) {
            throw new BizException(400, "最后一条消息必须是提问");
        }
        return result;
    }

    /** 解析决策 JSON;任何异常都返回 null,按"直接回答"处理,不让整轮对话失败 */
    private Decision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            String action = node.path("action").asText("").strip().toLowerCase();
            if (!"tool".equals(action)) {
                return null;
            }
            Map<String, Object> args = Map.of();
            JsonNode argsNode = node.path("args");
            if (argsNode.isObject()) {
                args = objectMapper.convertValue(argsNode, new TypeReference<Map<String, Object>>() {
                });
            }
            return new Decision(action, node.path("tool").asText("").strip(), args);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Agent 决策 JSON 解析失败,按直接回答处理 raw={}", text);
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

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_OBSERVATION_CHARS) {
            return text == null ? "" : text;
        }
        return text.substring(0, MAX_OBSERVATION_CHARS) + "…(已截断)";
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private record Decision(String action, String tool, Map<String, Object> args) {
    }

    private record Observation(String name, String summary, String data) {
    }
}
