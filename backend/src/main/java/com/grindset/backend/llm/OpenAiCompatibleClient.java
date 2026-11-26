package com.grindset.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议的流式实现。DeepSeek / 千问 / GLM / MiniMax / 豆包 都支持同一套
 * POST {baseUrl}/chat/completions + SSE 增量返回,所以一个类覆盖全部厂商。
 *
 * 返回体形如:
 * data: {"choices":[{"delta":{"content":"你"}}]}
 * data: {"choices":[{"delta":{"content":"好"}}]}
 * data: [DONE]
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    /** 上游接口整体超时;流式请求拿到响应头后即可持续读,不会被这个值掐断 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    /** 非流式解析可能要输出上千 token,给足 120 秒 */
    private static final Duration COMPLETE_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 确认支持 stream_options.include_usage 的厂商。
     * 其余厂商不传该参数,避免未知参数被直接拒绝;拿不到 usage 时走字符估算。
     */
    private static final Set<String> STREAM_USAGE_PROVIDERS = Set.of("deepseek", "qwen", "doubao");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * 流式只说人话,不处理 tools:工具调用必须一次性拿到完整的 tool_calls,
     * 流式边吐字边调工具做不到,所以工具场景统一走 completeWithTools。
     */
    @Override
    public LlmUsage streamChat(LlmTarget target, String model, List<ChatMessage> messages,
                               Consumer<String> onDelta) {
        String url = trimTrailingSlash(target.baseUrl()) + "/chat/completions";
        // 流式请求不带 tools:工具调用必须一次性拿完整 tool_calls,见类注释
        // 只有确认支持 include_usage 的厂商才带 stream_options,否则可能被未知参数拒掉
        String body = buildRequestBody(model, messages, true, List.of(),
                STREAM_USAGE_PROVIDERS.contains(target.providerId()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                // 五家都用 Bearer 方式鉴权
                .header("Authorization", "Bearer " + target.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("请求模型服务失败 provider={} url={}", target.providerId(), url, e);
            throw new LlmException(502, "无法连接模型服务,请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(502, "请求模型服务被中断");
        }

        // usage 只在开了 include_usage 的厂商最后一帧出现;没拿到就按字符估算
        LlmUsage usage = null;
        StringBuilder output = new StringBuilder();

        try (InputStream in = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {

            // 4xx/5xx 时上游返回的是普通 JSON,先读完再映射成中文错误,避免连接泄漏
            if (response.statusCode() >= 400) {
                String errBody = readAll(reader);
                log.warn("模型服务返回错误 provider={} status={} body={}",
                        target.providerId(), response.statusCode(), errBody);
                throw mapUpstreamError(target, response.statusCode(), errBody);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    // 空行、注释行(: ping)、event: 行都跳过
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                // [DONE] 是协议约定的正常结束标记,不是业务数据
                if ("[DONE]".equals(data)) {
                    break;
                }
                // 一条 data 可能只有 role / finish_reason / usage,没有文本;解析结果为空就跳过
                StreamChunk chunk = parseChunk(data, target.providerId());
                if (chunk.usage() != null) {
                    usage = chunk.usage();
                }
                for (String text : chunk.texts()) {
                    output.append(text);
                    onDelta.accept(text);
                }
            }
        } catch (IOException e) {
            // 读流过程断开:上游异常或客户端已离开,交给上层处理
            throw new LlmException(502, "模型服务连接中断,请重试");
        }
        return usage != null ? usage : estimateUsage(messages, output.length());
    }

    /**
     * 唯一的非流式入口:普通文本场景(简历/面试)和 Agent 决策都走这里。
     * 没有工具时 tools 传空,返回的 toolCalls 也为空。
     */
    @Override
    public LlmCompletion completeWithTools(LlmTarget target, String model, List<ChatMessage> messages,
                                           List<Map<String, Object>> tools) {
        String url = trimTrailingSlash(target.baseUrl()) + "/chat/completions";
        String body = buildRequestBody(model, messages, false, tools, false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(COMPLETE_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + target.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("请求模型服务失败 provider={} url={}", target.providerId(), url, e);
            throw new LlmException(502, "无法连接模型服务,请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(502, "请求模型服务被中断");
        }

        if (response.statusCode() >= 400) {
            log.warn("模型服务返回错误 provider={} status={} body={}",
                    target.providerId(), response.statusCode(), response.body());
            throw mapUpstreamError(target, response.statusCode(), response.body());
        }

        // 上游 HTTP 200 也可能返回非 JSON(网关错误页等),先统一转成 JSON 树
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException e) {
            log.warn("模型服务返回非 JSON provider={} body={}", target.providerId(), response.body());
            throw new LlmException(502, "模型服务返回内容无法解析,请稍后再试");
        }

        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new LlmException(502, error.path("message").asText("模型服务返回错误"));
        }

        JsonNode message = root.path("choices").path(0).path("message");
        // 模型调工具时 content 可能是 null,不能按普通文本响应处理;
        // tool_calls 里的 arguments 是 JSON 字符串,这里原样带回,解析交给上层
        String content = message.path("content").isTextual() ? message.path("content").asText() : "";
        List<LlmToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode node : toolCalls) {
                String name = node.path("function").path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                String id = node.path("id").asText("");
                // 少数渠道不返回 id,补一个稳定的本地 id,保证 assistant/tool 两边能对上
                if (id.isBlank()) {
                    id = "call_" + calls.size();
                }
                calls.add(new LlmToolCall(id, name,
                        node.path("function").path("arguments").asText("{}")));
            }
        }

        // 正文和工具调用都空,说明上游返回了空壳,不能让上层拿到空结果
        if (content.isBlank() && calls.isEmpty()) {
            throw new LlmException(502, "模型服务返回内容为空,请稍后再试");
        }
        LlmUsage usage = readUsage(root);
        return new LlmCompletion(content, calls,
                usage != null ? usage : estimateUsage(messages, content.length()));
    }

    /** 一帧 SSE 的解析结果:增量文本 + 可能出现的 usage(开 include_usage 时最后一帧才有) */
    private record StreamChunk(List<String> texts, LlmUsage usage) {
    }

    /** 从一条 SSE data 里取出增量文本和 usage,取不到就返回空列表 */
    private StreamChunk parseChunk(String data, String providerId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (IOException e) {
            // 单条脏数据不打断整个流
            log.debug("忽略无法解析的流数据 provider={} data={}", providerId, data);
            return new StreamChunk(List.of(), null);
        }

        // 流里也可能夹错误帧;遇到要立刻中断,不能当成空 delta 吞掉
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText("模型服务返回错误");
            throw new LlmException(502, message);
        }

        LlmUsage usage = readUsage(root);
        // 只取 delta.content;reasoning_content / role 这些不需要下发给前端
        JsonNode content = root.path("choices").path(0).path("delta").path("content");
        if (content.isTextual() && !content.asText().isEmpty()) {
            return new StreamChunk(List.of(content.asText()), usage);
        }
        return new StreamChunk(List.of(), usage);
    }

    /**
     * 从响应体里读 usage;缺失或全为 0 返回 null,由上层按字符估算。
     * DeepSeek 的缓存命中字段是 prompt_cache_hit_tokens,OpenAI 系是 prompt_tokens_details.cached_tokens。
     */
    private static LlmUsage readUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        int input = usage.path("prompt_tokens").asInt(0);
        int output = usage.path("completion_tokens").asInt(0);
        if (input == 0 && output == 0) {
            return null;
        }
        int cached = usage.path("prompt_cache_hit_tokens").asInt(0);
        if (cached == 0) {
            cached = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        }
        return new LlmUsage(input, output, Math.min(cached, input), false);
    }

    /**
     * 上游没返回 usage 时的兜底估算:中英文混合按 2 个字符约 1 token。
     * 只用于记账兜底并标记 estimated,不能当作准确账单。
     */
    private static LlmUsage estimateUsage(List<ChatMessage> messages, int outputChars) {
        int inputChars = 0;
        for (ChatMessage message : messages) {
            if (message.content() != null) {
                inputChars += message.content().length();
            }
        }
        return new LlmUsage(estimateTokens(inputChars), estimateTokens(outputChars), 0, true);
    }

    private static int estimateTokens(int chars) {
        return chars <= 0 ? 0 : (int) Math.ceil(chars / 2.0);
    }

    /**
     * 按 OpenAI 协议拼请求体。消息有三种形态:
     * 1. 普通消息:role + content;
     * 2. 助手工具调用:content 用空串(部分国产厂商不接受 null)+ tool_calls;
     * 3. 工具结果:role=tool + tool_call_id + content,id 必须和上面的调用一一对应。
     */
    private String buildRequestBody(String model, List<ChatMessage> messages, boolean stream,
                                    List<Map<String, Object>> tools, boolean includeUsage) {
        List<Map<String, Object>> payloadMessages = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role());
            // 顺序不能乱:先判 tool 消息,再判 assistant 的 tool_calls,最后才是普通消息
            if (ChatMessage.ROLE_TOOL.equals(message.role())) {
                // 工具结果必须带 id,上游靠它把结果和上一轮的调用对上
                item.put("tool_call_id", message.toolCallId());
                item.put("content", message.content() == null ? "" : message.content());
            } else if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                // assistant 要把上一轮调用清单原样带回,content 置空即可
                item.put("content", message.content() == null ? "" : message.content());
                List<Map<String, Object>> calls = new ArrayList<>(message.toolCalls().size());
                for (LlmToolCall call : message.toolCalls()) {
                    calls.add(Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of(
                                    "name", call.name(),
                                    "arguments", call.arguments() == null ? "{}" : call.arguments())));
                }
                item.put("tool_calls", calls);
            } else {
                item.put("content", message.content() == null ? "" : message.content());
            }
            payloadMessages.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", payloadMessages);
        payload.put("stream", stream);
        // 让厂商在最后一帧返回 usage;不支持的厂商不传,拿不到就按字符估算
        if (stream && includeUsage) {
            payload.put("stream_options", Map.of("include_usage", true));
        }
        // 只有 Agent 决策轮会传 tools;普通对话不传,避免模型突然想调工具
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        if (!stream) {
            // 结构化解析要求结果稳定,温度降到 0
            payload.put("temperature", 0);
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            // 纯内存对象序列化,理论上不会失败
            throw new LlmException(500, "构造模型请求失败");
        }
    }

    /** 上游 4xx/5xx 转成用户能看懂的中文提示 */
    private LlmException mapUpstreamError(LlmTarget target, int status, String errBody) {
        String name = target.displayName() == null || target.displayName().isBlank()
                ? target.providerId()
                : target.displayName();
        if (status == 401 || status == 403) {
            return new LlmException(502, name + " 的 API Key 无效或无权限,请检查服务端配置", status);
        }
        if (status == 404) {
            return new LlmException(502, name + " 接口地址或模型 ID 不正确,请检查配置", status);
        }
        if (status == 429) {
            return new LlmException(502, name + " 请求过于频繁或额度不足,请稍后再试", status);
        }
        if (status >= 500) {
            return new LlmException(502, name + " 服务暂时不可用,请稍后再试", status);
        }
        // 其余情况尽量把上游原文带出来,便于排查模型 ID / 参数问题
        String detail = errBody == null ? "" : errBody.strip();
        if (detail.length() > 200) {
            detail = detail.substring(0, 200);
        }
        return new LlmException(502, name + " 拒绝了请求" + (detail.isEmpty() ? "" : ":" + detail), status);
    }

    private static String readAll(BufferedReader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.strip();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
