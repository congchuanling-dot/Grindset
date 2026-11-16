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

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
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
}
