package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 简历解析配置。provider + model 都配置时固定使用;
 * 否则取模型目录里第一个可用厂商的第一个模型。
 */
@Component
@ConfigurationProperties(prefix = "app.resume")
public class ResumeProperties {

    private Llm llm = new Llm();

    private Oss oss = new Oss();

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Oss getOss() {
        return oss;
    }

    public void setOss(Oss oss) {
        this.oss = oss;
    }

    public static class Llm {

        private String provider = "";

        private String model = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    /**
     * 简历原件的 OSS 兜底配置。数据库 storage_config 优先,
     * 这里只给本地开发用;四个核心参数不齐全时视为未配置。
     */
    public static class Oss {

        private String endpoint = "";

        private String bucket = "";

        private String accessKeyId = "";

        private String accessKeySecret = "";

        private String dirPrefix = "resumes";

        private int urlExpireSeconds = 3600;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getDirPrefix() {
            return dirPrefix;
        }

        public void setDirPrefix(String dirPrefix) {
            this.dirPrefix = dirPrefix;
        }

        public int getUrlExpireSeconds() {
            return urlExpireSeconds;
        }

        public void setUrlExpireSeconds(int urlExpireSeconds) {
            this.urlExpireSeconds = urlExpireSeconds;
        }
    }
}
