package com.grindset.backend.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grindset.backend.config.AsrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 与火山引擎豆包流式识别(bigmodel_async)的一次会话。
 *
 * <p>协议帧:4 字节头 + [序号] + 4 字节长度 + payload;首包 JSON+gzip,
 * 音频为裸 PCM(s16le/16k/单声道),最后发空负包。注意火山把首包算作序号 1,
 * 所以音频序号从 2 开始。</p>
 */
public class VolcAsrClient {

    private static final Logger log = LoggerFactory.getLogger(VolcAsrClient.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).build();
    private static final int RESULT_TIMEOUT_SECONDS = 8;

    private final AsrProperties properties;
    private final ObjectMapper mapper;
    private final Listener listener;

    private final AtomicInteger sequence = new AtomicInteger(2);
    private final ConcurrentLinkedDeque<byte[]> pending = new ConcurrentLinkedDeque<>();
    private final ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
    private final CompletableFuture<String> finalText = new CompletableFuture<>();
    private volatile WebSocket socket;
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile String lastPartial = "";

    public interface Listener {

        void onReady();

        void onPartial(String text);

        void onFinal(String text, long durationMs);

        void onError(String message);
    }

    public VolcAsrClient(AsrProperties properties, ObjectMapper mapper, Listener listener) {
        this.properties = properties;
        this.mapper = mapper;
        this.listener = listener;
    }

    public void connect() throws Exception {
        AsrProperties.Volcengine volc = properties.volcengine();
        int timeout = Math.max(3, volc.connectTimeoutSeconds());
        WebSocket ws = HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .header("X-Api-Key", volc.apiKey())
                .header("X-Api-Resource-Id", volc.resourceId())
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .header("X-Api-Sequence", "-1")
                .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                .buildAsync(URI.create(volc.endpoint()), new VolcListener())
                .get(timeout + 2L, TimeUnit.SECONDS);
        this.socket = ws;
        drainPending();
    }

    public void sendAudio(byte[] pcm) {
        if (closed || pcm == null || pcm.length == 0) {
            return;
        }
        WebSocket ws = socket;
        if (!ready || ws == null) {
            pending.addLast(pcm);
        } else {
            send(ws, frame(0x2, 0x1, 0, 0, sequence.getAndIncrement(), pcm));
        }
    }

    /** 发负包并等最终文本;超时用最后一次中间结果兜底 */
    public CompletableFuture<String> finish() {
        WebSocket ws = socket;
        if (!closed && ws != null) {
            send(ws, frame(0x2, 0x2, 0, 0, 0, new byte[0]));
        }
        return finalText.orTimeout(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(error -> lastPartial);
    }

    public void close() {
        closed = true;
        WebSocket ws = socket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
                // 连接可能已断开
            }
        }
        if (!finalText.isDone()) {
            finalText.complete(lastPartial);
        }
    }

    private void send(WebSocket ws, byte[] data) {
        try {
            ws.sendBinary(ByteBuffer.wrap(data), true);
        } catch (Exception e) {
            log.warn("发送语音识别音频帧失败", e);
        }
    }

    private void drainPending() {
        WebSocket ws = socket;
        if (ws == null) {
            return;
        }
        byte[] item;
        while ((item = pending.pollFirst()) != null) {
            send(ws, frame(0x2, 0x1, 0, 0, sequence.getAndIncrement(), item));
        }
    }

    private final class VolcListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            try {
                ObjectNode root = mapper.createObjectNode();
                root.putObject("user").put("uid", "grindset");
                ObjectNode audio = root.putObject("audio");
                audio.put("format", "pcm");
                audio.put("codec", "raw");
                audio.put("rate", 16000);
                audio.put("bits", 16);
                audio.put("channel", 1);
                ObjectNode request = root.putObject("request");
                request.put("model_name", "bigmodel");
                request.put("enable_itn", true);
                request.put("enable_punc", true);
                request.put("enable_ddc", true);
                request.put("enable_nonstream", true);
                request.put("result_type", "full");
                send(webSocket, frame(0x1, 0x0, 0x1, 0x1, 0, gzip(mapper.writeValueAsBytes(root))));
                ready = true;
                drainPending();
                listener.onReady();
            } catch (Exception e) {
                log.warn("语音识别首包发送失败", e);
                listener.onError("语音识别初始化失败，请重试");
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                handlePayload(data.toString(), false);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            try {
                frameBuffer.write(chunk);
                if (last) {
                    byte[] all = frameBuffer.toByteArray();
                    frameBuffer.reset();
                    handleFrame(all);
                }
            } catch (Exception e) {
                log.warn("解析语音识别响应失败", e);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            if (!finalText.isDone()) {
                finalText.complete(lastPartial);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
            log.warn("语音识别连接异常", error);
            listener.onError("语音识别连接异常，请稍后重试");
            if (!finalText.isDone()) {
                finalText.completeExceptionally(error);
            }
        }
    }

    private void handleFrame(byte[] data) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int b0 = buf.get() & 0xFF;
        int b1 = buf.get() & 0xFF;
        int b2 = buf.get() & 0xFF;
        buf.get();
        int type = (b1 >> 4) & 0x0F;
        int flags = b1 & 0x0F;
        int compression = b2 & 0x0F;
        buf.position(Math.max((b0 & 0x0F) * 4, 4));
        if (flags == 1 || flags == 3) {
            buf.position(buf.position() + 4);
        }
        if (type == 0xF) {
            int code = buf.getInt();
            int size = buf.getInt();
            byte[] payload = new byte[size];
            buf.get(payload);
            String message = errorMessage(code);
            log.warn("语音识别返回错误, code={}, detail={}", code,
                    new String(payload, java.nio.charset.StandardCharsets.UTF_8));
            listener.onError(message);
            if (!finalText.isDone()) {
                finalText.completeExceptionally(new IllegalStateException(message));
            }
            return;
        }
        int size = buf.getInt();
        byte[] payload = new byte[size];
        buf.get(payload);
        boolean last = flags == 0x2 || flags == 0x3;
        handlePayload(new String(compression == 1 ? gunzip(payload) : payload,
                java.nio.charset.StandardCharsets.UTF_8), last);
    }

    private void handlePayload(String payload, boolean headerLast) {
        try {
            JsonNode root = mapper.readTree(payload);
            String text = root.path("result").path("text").asText("");
            long duration = root.path("audio_info").path("duration").asLong(0);
            boolean last = headerLast || root.path("is_last_package").asBoolean(false);
            if (!text.isBlank()) {
                lastPartial = text;
            }
            if (last) {
                String result = text.isBlank() ? lastPartial : text;
                listener.onFinal(result, duration);
                finalText.complete(result);
            } else if (!text.isBlank()) {
                listener.onPartial(text);
            }
        } catch (Exception e) {
            log.debug("语音识别响应不是 JSON: {}", payload);
        }
    }

    /** 帧结构:header(4) + [序号(4)] + 长度(4) + payload */
    private static byte[] frame(int type, int flags, int serialization, int compression,
                                int sequence, byte[] payload) {
        boolean hasSequence = flags == 1 || flags == 3;
        ByteBuffer buf = ByteBuffer.allocate(4 + (hasSequence ? 4 : 0) + 4 + payload.length);
        buf.put((byte) 0x11);
        buf.put((byte) ((type << 4) | flags));
        buf.put((byte) ((serialization << 4) | compression));
        buf.put((byte) 0);
        if (hasSequence) {
            buf.putInt(sequence);
        }
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (Exception e) {
            return data;
        }
    }

    private static String errorMessage(int code) {
        return switch (code) {
            case 20000003 -> "没有识别到声音，请再说一遍";
            case 45000001, 45000002 -> "语音数据异常，请重试";
            case 45000151 -> "音频格式不支持";
            case 55000031 -> "语音识别服务繁忙，请稍后重试";
            default -> "语音识别失败（错误码 " + code + "）";
        };
    }
}
