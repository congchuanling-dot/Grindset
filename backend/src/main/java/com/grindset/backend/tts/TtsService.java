package com.grindset.backend.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.config.TtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 豆包语音合成(HTTP 单向流式 SSE)。
 *
 * <p>请求一次文本,服务端按 SSE 逐行推 JSON,音频字段是 base64 的 PCM;
 * 调用方按块回调即可边收边播。失败统一转成 502,由前端提示重试。</p>
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    /** 服务端结束码,收到即停止读取 */
    private static final int COMPLETE_CODE = 20000000;

    private final TtsProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TtsService(TtsProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public boolean available() {
        return properties.available();
    }

    /**
     * 合成一段文本,音频按块回调(裸 PCM,s16le/24k/单声道)。
     * 阻塞调用,必须放在请求线程或虚拟线程里跑。
     */
    public void synthesize(String text, Consumer<byte[]> onAudio) {
        if (!available()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "语音合成未配置");
        }

        ObjectNode body = mapper.createObjectNode();
        body.putObject("user").put("uid", "grindset");
        ObjectNode req = body.putObject("req_params");
        req.put("text", text);
        req.put("speaker", properties.speaker());
        req.putObject("audio_params").put("format", "pcm").put("sample_rate", 24000);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                    .timeout(Duration.ofSeconds(Math.max(10, properties.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("X-Api-Key", properties.apiKey())
                    .header("X-Api-Resource-Id", properties.resourceId())
                    .header("X-Api-Request-Id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                    .build();
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "语音合成请求构造失败");
        }

        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.BAD_GATEWAY, "语音合成连接中断");
        } catch (Exception e) {
            log.warn("语音合成请求失败", e);
            throw new BizException(ErrorCode.BAD_GATEWAY, "语音合成连接失败");
        }

        try (InputStream bodyStream = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            if (response.statusCode() != 200) {
                throw new BizException(ErrorCode.BAD_GATEWAY,
                        "语音合成失败(" + response.statusCode() + ")");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(payload);
                } catch (Exception e) {
                    continue;
                }
                int code = node.path("code").asInt(0);
                String audio = node.path("data").asText("");
                if (!audio.isEmpty()) {
                    onAudio.accept(Base64.getDecoder().decode(audio));
                }
                if (code == COMPLETE_CODE) {
                    return;
                }
                if (code != 0 && audio.isEmpty()) {
                    String message = node.path("message").asText("语音合成失败");
                    log.warn("语音合成返回错误 code={} message={}", code, message);
                    throw new BizException(ErrorCode.BAD_GATEWAY, message);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            // 回调里抛出的"客户端已断开"信号,原样上抛
            throw e;
        } catch (Exception e) {
            // 连接被主动关闭等 IO 收尾,不需要报错日志刷屏
            log.debug("语音合成流结束: {}", e.getMessage());
        }
    }
}
