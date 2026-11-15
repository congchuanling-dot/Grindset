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
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public void streamChat(LlmTarget target, String model, List<ChatMessage> messages,
                           Consumer<String> onDelta) {
        String url = trimTrailingSlash(target.baseUrl()) + "/chat/completions";
        String body = buildRequestBody(model, messages);

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

        try (InputStream in = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {

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
                if ("[DONE]".equals(data)) {
                    break;
                }
                for (String text : parseDelta(data, target.providerId())) {
                    onDelta.accept(text);
                }
            }
        } catch (IOException e) {
            // 读流过程断开:上游异常或客户端已离开,交给上层处理
            throw new LlmException(502, "模型服务连接中断,请重试");
        }
    }

    /** 从一条 SSE data 里取出增量文本,取不到就返回空列表 */
    private List<String> parseDelta(String data, String providerId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (IOException e) {
            // 单条脏数据不打断整个流
            log.debug("忽略无法解析的流数据 provider={} data={}", providerId, data);
            return List.of();
        }

        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText("模型服务返回错误");
            throw new LlmException(502, message);
        }

        JsonNode content = root.path("choices").path(0).path("delta").path("content");
        if (content.isTextual() && !content.asText().isEmpty()) {
            return List.of(content.asText());
        }
        return List.of();
    }

    private String buildRequestBody(String model, List<ChatMessage> messages) {
        List<Map<String, String>> payloadMessages = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.role());
            item.put("content", message.content());
            payloadMessages.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", payloadMessages);
        payload.put("stream", true);

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
