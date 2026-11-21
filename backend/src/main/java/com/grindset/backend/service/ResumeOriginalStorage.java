package com.grindset.backend.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.grindset.backend.common.ResumeFileType;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;

/**
 * 简历原件的阿里云 OSS 存储。私有桶读,对外只下发带时效的签名 URL。
 * 所有失败都只记日志并返回 null,由调用方跳过原件、继续解析主流程。
 */
@Service
public class ResumeOriginalStorage {

    private static final Logger log = LoggerFactory.getLogger(ResumeOriginalStorage.class);

    private final StorageConfigService configService;

    /** 按配置指纹缓存客户端,管理接口改完配置后下一次调用自动重建 */
    private volatile CachedClient cached;

    public ResumeOriginalStorage(StorageConfigService configService) {
        this.configService = configService;
    }

    /**
     * 上传原件(流式,不把整份文件读进堆)。
     *
     * @return object key;未配置 OSS、上传失败或文件为空时返回 null
     */
    public String upload(long userId, long resumeId, ResumeFileType fileType, Path file, long size) {
        if (file == null || size <= 0) {
            return null;
        }
        StorageConfigService.ResolvedOss config = configService.resolve();
        if (config == null) {
            return null;
        }
        String key = objectKey(config.dirPrefix(), userId, resumeId, fileType);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType(fileType));
            try (InputStream in = Files.newInputStream(file)) {
                client(config).putObject(config.bucket(), key, in, metadata);
            }
            return key;
        } catch (Exception e) {
            log.warn("简历原件上传 OSS 失败,已跳过原件存储 resumeId={} type={}", resumeId, fileType, e);
            return null;
        }
    }

    /**
     * 把 OSS 原件下载到本地临时文件,供解析线程抽取文本。
     *
     * @return 成功返回 true;未配置、对象不存在或网络失败返回 false
     */
    public boolean downloadTo(String objectKey, Path target) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        StorageConfigService.ResolvedOss config = configService.resolve();
        if (config == null) {
            return false;
        }
        try (InputStream in = client(config).getObject(config.bucket(), objectKey).getObjectContent()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.warn("下载 OSS 简历原件失败 key={}", objectKey, e);
            return false;
        }
    }

    /** 内嵌预览 URL,失败返回 null */
    public String previewUrl(String objectKey) {
        return signedUrl(objectKey, false, null);
    }

    /** 下载 URL,带原文件名;失败返回 null */
    public String downloadUrl(String objectKey, String fileName) {
        return signedUrl(objectKey, true, fileName);
    }

    /**
     * 打开原件输入流,供后端代理预览。
     * OSS 默认域名会强制下载,所以 PDF 预览不能直接把签名 URL 丢给 iframe。
     * 未配置、对象不存在或读取失败都返回 null。
     */
    public InputStream open(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        StorageConfigService.ResolvedOss config = configService.resolve();
        if (config == null) {
            return null;
        }
        try {
            return client(config).getObject(config.bucket(), objectKey).getObjectContent();
        } catch (Exception e) {
            log.warn("读取 OSS 简历原件失败 key={}", objectKey, e);
            return null;
        }
    }

    /** 删除对象,失败只记日志(数据库记录已删,不能回滚) */
    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        StorageConfigService.ResolvedOss config = configService.resolve();
        if (config == null) {
            return;
        }
        try {
            client(config).deleteObject(config.bucket(), objectKey);
        } catch (Exception e) {
            log.warn("删除 OSS 简历原件失败,对象可能残留 key={}", objectKey, e);
        }
    }

    private String signedUrl(String objectKey, boolean download, String fileName) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        StorageConfigService.ResolvedOss config = configService.resolve();
        if (config == null) {
            return null;
        }
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    config.bucket(), objectKey, HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + config.urlExpireSeconds() * 1000L));
            ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
            if (download) {
                headers.setContentDisposition(attachmentDisposition(fileName));
            } else {
                // 预览必须显式声明 inline,否则浏览器可能按附件下载,iframe 里一片空白
                headers.setContentDisposition("inline");
            }
            request.setResponseHeaders(headers);
            return client(config).generatePresignedUrl(request).toString();
        } catch (Exception e) {
            log.warn("生成 OSS 签名 URL 失败 key={}", objectKey, e);
            return null;
        }
    }

    private OSS client(StorageConfigService.ResolvedOss config) {
        String fingerprint = config.endpoint() + '\n' + config.bucket() + '\n'
                + config.accessKeyId() + '\n' + config.accessKeySecret();
        CachedClient current = cached;
        if (current != null && current.fingerprint().equals(fingerprint)) {
            return current.client();
        }
        synchronized (this) {
            if (cached != null && cached.fingerprint().equals(fingerprint)) {
                return cached.client();
            }
            if (cached != null) {
                shutdown(cached.client());
            }
            ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
            conf.setConnectionTimeout(10_000);
            conf.setSocketTimeout(30_000);
            conf.setMaxConnections(50);
            OSS client = new OSSClientBuilder().build(config.endpoint(), config.accessKeyId(),
                    config.accessKeySecret(), conf);
            cached = new CachedClient(fingerprint, client);
            return client;
        }
    }

    private static String objectKey(String dirPrefix, long userId, long resumeId, ResumeFileType fileType) {
        String ext = fileType == null ? "bin" : fileType.extension();
        return dirPrefix + "/" + userId + "/" + resumeId + "." + ext;
    }

    private static String contentType(ResumeFileType fileType) {
        return fileType == null ? "application/octet-stream" : fileType.contentType();
    }

    /** 后端代理预览时的响应类型 */
    public static String contentTypeOf(ResumeFileType fileType) {
        return contentType(fileType);
    }

    /** 后端代理预览时的响应头:inline 才能被浏览器直接渲染 */
    public static String inlineDisposition(String fileName) {
        return disposition("inline", fileName);
    }

    /** 中文文件名走 filename*,同时给一个 ASCII 兜底名 */
    private static String attachmentDisposition(String fileName) {
        return disposition("attachment", fileName);
    }

    private static String disposition(String type, String fileName) {
        String name = fileName == null || fileName.isBlank() ? "resume" : fileName.strip();
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static void shutdown(OSS client) {
        try {
            client.shutdown();
        } catch (Exception e) {
            log.warn("关闭旧 OSS 客户端失败", e);
        }
    }

    @PreDestroy
    public void close() {
        CachedClient current = cached;
        if (current != null) {
            shutdown(current.client());
            cached = null;
        }
    }

    private record CachedClient(String fingerprint, OSS client) {
    }
}
