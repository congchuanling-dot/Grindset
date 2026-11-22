package com.grindset.backend.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.config.AsrProperties;
import com.grindset.backend.security.JwtService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 语音识别公共入口(浏览器 WebSocket)。
 *
 * <p>连接:ws://host/api/asr/stream?token=&lt;access token&gt;&amp;maxSeconds=60。
 * 上行二进制裸 PCM(s16le/16k/单声道),结束发 {"type":"finish"};
 * 下行 ready / partial / final / error。任何页面都可以直接复用该入口。</p>
 *
 * <p>token 走 query 是因为浏览器 WebSocket 不能带 Authorization 头;
 * access token 本身只有 30 分钟有效期,风险可控。</p>
 */
@Component
public class AsrWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);

    private final AsrProperties properties;
    private final JwtService jwtService;
    private final ObjectMapper mapper;

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "asr-session-timer");
        thread.setDaemon(true);
        return thread;
    });

    public AsrWebSocketHandler(AsrProperties properties, JwtService jwtService, ObjectMapper mapper) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        MultiValueMap<String, String> query = query(session.getUri());
        Long userId = parseUser(query.getFirst("token"));
        if (userId == null) {
            errorAndClose(session, "登录已过期，请刷新页面后重试");
            return;
        }
        if (!properties.available()) {
            errorAndClose(session, "语音识别未配置");
            return;
        }
        if (!activeUsers.add(userId)) {
            errorAndClose(session, "上一段语音还在识别中，请稍后再试");
            return;
        }

        Client client = new Client(session, userId, maxSeconds(query.getFirst("maxSeconds")),
                properties.session().maxAudioBytes());
        clients.put(session.getId(), client);

        client.volc = new VolcAsrClient(properties, mapper, new VolcAsrClient.Listener() {
            @Override
            public void onReady() {
                if (!session.isOpen()) {
                    return;
                }
                send(session, message("ready"));
                client.timeout = scheduler.schedule(() -> finish(client),
                        client.maxSeconds, TimeUnit.SECONDS);
            }

            @Override
            public void onPartial(String text) {
                Map<String, Object> payload = message("partial");
                payload.put("text", text);
                send(session, payload);
            }

            @Override
            public void onFinal(String text, long durationMs) {
                sendFinal(client, text, durationMs);
            }

            @Override
            public void onError(String message) {
                client.errored.set(true);
                Map<String, Object> payload = message("error");
                payload.put("message", message);
                send(session, payload);
                scheduler.schedule(() -> closeClient(client), 100, TimeUnit.MILLISECONDS);
            }
        });

        Thread.ofVirtual().name("asr-connect-" + userId).start(() -> {
            try {
                client.volc.connect();
            } catch (Exception e) {
                log.warn("语音识别连接火山引擎失败, userId={}", userId, e);
                Map<String, Object> payload = message("error");
                payload.put("message", "语音识别连接失败，请稍后重试");
                send(session, payload);
                closeClient(client);
            }
        });
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Client client = clients.get(session.getId());
        if (client == null || client.finished.get()) {
            return;
        }
        byte[] pcm = new byte[message.getPayloadLength()];
        message.getPayload().get(pcm);
        if (client.received + pcm.length > client.maxAudioBytes) {
            Map<String, Object> payload = message("error");
            payload.put("message", "这段语音太长了，请缩短后重试");
            send(session, payload);
            finish(client);
            return;
        }
        client.received += pcm.length;
        client.volc.sendAudio(pcm);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Client client = clients.get(session.getId());
        if (client == null) {
            return;
        }
        try {
            if ("finish".equals(mapper.readTree(message.getPayload()).path("type").asText())) {
                finish(client);
            }
        } catch (Exception e) {
            log.debug("忽略无法解析的语音控制消息: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Client client = clients.get(session.getId());
        if (client != null) {
            closeClient(client);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Client client = clients.get(session.getId());
        if (client != null) {
            closeClient(client);
        }
    }

    private void finish(Client client) {
        if (!client.finished.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout(client);
        client.volc.finish().whenComplete((text, error) -> {
            if (client.closed.get() || client.finalSent.get() || client.errored.get()) {
                return;
            }
            if (text == null || text.isBlank()) {
                Map<String, Object> payload = message("error");
                payload.put("message", "没有识别到声音，请再说一遍");
                send(client.session, payload);
                closeClient(client);
                return;
            }
            sendFinal(client, text, 0);
        });
    }

    private void sendFinal(Client client, String text, long durationMs) {
        if (!client.finalSent.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout(client);
        Map<String, Object> payload = message("final");
        payload.put("text", text);
        payload.put("durationMs", durationMs);
        send(client.session, payload);
        scheduler.schedule(() -> closeClient(client), 100, TimeUnit.MILLISECONDS);
    }

    private void closeClient(Client client) {
        if (!client.closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout(client);
        client.volc.close();
        activeUsers.remove(client.userId);
        clients.remove(client.session.getId());
        try {
            if (client.session.isOpen()) {
                client.session.close(CloseStatus.NORMAL);
            }
        } catch (Exception ignored) {
            // 连接可能已经断开
        }
    }

    private void errorAndClose(WebSocketSession session, String message) {
        Map<String, Object> payload = message("error");
        payload.put("message", message);
        send(session, payload);
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // 连接可能已经断开
        }
    }

    private void cancelTimeout(Client client) {
        ScheduledFuture<?> timeout = client.timeout;
        if (timeout != null) {
            timeout.cancel(false);
            client.timeout = null;
        }
    }

    private void send(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String text = mapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.debug("发送语音识别消息失败: {}", e.getMessage());
        }
    }

    private static Map<String, Object> message(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        return payload;
    }

    private static MultiValueMap<String, String> query(URI uri) {
        return uri == null ? new org.springframework.util.LinkedMultiValueMap<>()
                : UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private Long parseUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtService.parse(token);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 调用方传的秒数会被服务端硬上限截断 */
    private int maxSeconds(String raw) {
        int hard = Math.max(10, properties.session().hardMaxSeconds());
        int requested = properties.session().defaultMaxSeconds();
        if (raw != null && !raw.isBlank()) {
            try {
                requested = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // 参数非法时用默认值
            }
        }
        return Math.max(5, Math.min(requested, hard));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private static final class Client {

        private final WebSocketSession session;
        private final long userId;
        private final int maxSeconds;
        private final long maxAudioBytes;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean finalSent = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean errored = new AtomicBoolean(false);

        private VolcAsrClient volc;
        private ScheduledFuture<?> timeout;
        private long received;

        private Client(WebSocketSession session, long userId, int maxSeconds, long maxAudioBytes) {
            this.session = session;
            this.userId = userId;
            this.maxSeconds = maxSeconds;
            this.maxAudioBytes = maxAudioBytes;
        }
    }
}
