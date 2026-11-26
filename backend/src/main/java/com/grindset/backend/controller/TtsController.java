package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.tts.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.STREAM_TIMEOUT_5_MIN_MS;

/**
 * 语音合成接口。
 * POST /api/tts  请求体 {text};SSE 流式返回音频块:delta(audio=base64 PCM)/ done / error。
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);
    private static final int MAX_TEXT_LENGTH = 500;

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    public record SpeakRequest(String text) {
    }

    @PostMapping
    public SseEmitter speak(@AuthenticationPrincipal LoginUser loginUser,
                            @RequestBody SpeakRequest request) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        String text = request == null || request.text() == null ? "" : request.text().strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "合成文本不能为空");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        String content = text;
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        Thread.ofVirtual().name("tts-sse-", 0).start(() -> run(content, emitter));
        return emitter;
    }

    private void run(String text, SseEmitter emitter) {
        try {
            ttsService.synthesize(text, pcm -> {
                try {
                    emitter.send(SseEmitter.event().name("delta")
                            .data(Map.of("audio", Base64.getEncoder().encodeToString(pcm))));
                } catch (IOException e) {
                    // 客户端打断:抛出去让上游停止读取
                    throw new ClientGoneException();
                }
            });
            emitter.send(SseEmitter.event().name("done").data(Map.of()));
            emitter.complete();
        } catch (ClientGoneException e) {
            log.debug("客户端已中断语音合成");
            emitter.complete();
        } catch (BizException e) {
            fail(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("语音合成失败", e);
            fail(emitter, "语音合成失败,请重试");
        }
    }

    private void fail(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(new IllegalStateException(message));
        }
    }

    /** 客户端断开后停止向上游写数据用的内部信号 */
    private static final class ClientGoneException extends RuntimeException {
    }
}
