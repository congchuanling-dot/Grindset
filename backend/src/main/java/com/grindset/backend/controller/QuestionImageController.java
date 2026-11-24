package com.grindset.backend.controller;

import com.grindset.backend.service.QuestionImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 题库配图的公开读取代理。
 * 私有 OSS 桶不能直接给 img 标签用,这里按 key 转发字节;key 由上传接口生成并校验格式。
 */
@RestController
@RequestMapping("/api/questions/images")
public class QuestionImageController {

    private final QuestionImageService imageService;

    public QuestionImageController(QuestionImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@RequestParam("key") String key) {
        try (InputStream in = imageService.open(key)) {
            if (in == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = in.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(mediaType(key))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static MediaType mediaType(String key) {
        if (key.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (key.endsWith(".jpg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (key.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (key.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (key.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (key.endsWith(".avif")) {
            return MediaType.parseMediaType("image/avif");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
