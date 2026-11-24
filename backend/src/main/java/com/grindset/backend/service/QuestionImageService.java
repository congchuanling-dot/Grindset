package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.QuestionImageUploadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 题库配图存储:图片统一落在 OSS 的 questions/ 前缀下,
 * 对外只暴露后端代理 URL,避免私有桶签名过期。
 */
@Service
public class QuestionImageService {

    private static final String PREFIX = "questions/";
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^questions/[a-f0-9]{64}\\.(png|jpg|gif|webp|svg|avif)$");
    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/svg+xml", ".svg",
            "image/avif", ".avif");

    private final ResumeOriginalStorage storage;

    public QuestionImageService(ResumeOriginalStorage storage) {
        this.storage = storage;
    }

    /** 上传一张配图,按内容 SHA-256 生成稳定 key,重复图片自动覆盖 */
    public QuestionImageUploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE, "图片不能超过 10MB");
        }
        String contentType = normalizeType(file.getContentType());
        String ext = extOf(contentType, file.getOriginalFilename());
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只支持 png/jpg/gif/webp/svg/avif 图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取图片失败");
        }
        String key = PREFIX + sha256(bytes) + ext;
        String uploaded = storage.uploadObject(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
        if (uploaded == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "OSS 未配置或上传失败");
        }
        String url = "/api/questions/images?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        return new QuestionImageUploadResult(key, url);
    }

    /** 只允许读取 questions/ 下由本服务生成的对象 */
    public InputStream open(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return null;
        }
        return storage.open(key);
    }

    private static String normalizeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";")[0].strip().toLowerCase();
    }

    private static String extOf(String contentType, String filename) {
        String ext = EXT_BY_TYPE.get(contentType);
        if (ext != null) {
            return ext;
        }
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String nameExt = lower.substring(dot);
        if (".jpeg".equals(nameExt)) {
            return ".jpg";
        }
        return EXT_BY_TYPE.containsValue(nameExt) ? nameExt : null;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
