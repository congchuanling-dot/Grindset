package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.entity.LlmChannel;
import com.grindset.backend.entity.LlmKey;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.SecretCipher;
import com.grindset.backend.mapper.LlmChannelMapper;
import com.grindset.backend.mapper.LlmKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 余额:账号级,不是 key 级。DeepSeek 提供 /user/balance 接口所以能自动拉,
 * 其余厂商(千问/GLM/MiniMax/豆包)没有稳定的余额查询接口,只能手工维护。
 */
@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final LlmChannelMapper channelMapper;
    private final LlmKeyMapper keyMapper;
    private final LlmProperties properties;
    private final SecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BalanceService(LlmChannelMapper channelMapper, LlmKeyMapper keyMapper, LlmProperties properties,
                          SecretCipher cipher, ObjectMapper objectMapper) {
        this.channelMapper = channelMapper;
        this.keyMapper = keyMapper;
        this.properties = properties;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 每 5 分钟拉一次标记为 AUTO 的渠道余额 */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 5 * 60 * 1000L)
    public void refreshAutoChannels() {
        List<LlmChannel> channels = channelMapper.selectList(new QueryWrapper<LlmChannel>()
                .eq("enabled", 1)
                .eq("balance_source", "AUTO"));
        for (LlmChannel channel : channels) {
            try {
                if (refresh(channel)) {
                    log.info("余额已刷新 provider={} channelId={}", channel.getProviderId(), channel.getId());
                }
            } catch (Exception e) {
                log.warn("刷新余额失败 provider={} channelId={}", channel.getProviderId(), channel.getId(), e);
            }
        }
    }

    /**
     * 拉取并写回单个渠道的余额。
     * @return true 表示成功更新;false 表示该厂商不支持或没有可用 key
     */
    public boolean refresh(LlmChannel channel) {
        if (!"deepseek".equals(channel.getProviderId())) {
            return false;
        }
        String apiKey = firstKey(channel.getId(), channel.getProviderId());
        if (apiKey == null) {
            return false;
        }

        String url = origin(channel.getBaseUrl()) + "/user/balance";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("查询余额失败 provider={} status={}", channel.getProviderId(), response.statusCode());
                return false;
            }
            JsonNode info = objectMapper.readTree(response.body()).path("balance_infos").path(0);
            if (!info.hasNonNull("total_balance")) {
                return false;
            }
            BigDecimal amount = new BigDecimal(info.path("total_balance").asText());

            LlmChannel patch = new LlmChannel();
            patch.setId(channel.getId());
            patch.setBalance(amount);
            patch.setBalanceCurrency(info.path("currency").asText("CNY"));
            patch.setBalanceUpdatedAt(LocalDateTime.now());
            channelMapper.updateById(patch);
            return true;
        } catch (IOException e) {
            log.warn("查询余额网络异常 provider={}", channel.getProviderId(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 取一把可用 key:优先库里的,其次环境变量 */
    private String firstKey(long channelId, String providerId) {
        List<LlmKey> keys = keyMapper.selectList(new QueryWrapper<LlmKey>()
                .eq("channel_id", channelId)
                .eq("status", LlmKeyService.STATUS_NORMAL)
                .orderByAsc("last_used_at")
                .last("LIMIT 1"));
        if (!keys.isEmpty()) {
            return cipher.decrypt(keys.get(0).getKeyCipher(), channelId);
        }
        Map<String, String> envKeys = properties.getApiKeys();
        String envKey = envKeys == null ? null : envKeys.get(providerId);
        return envKey == null || envKey.isBlank() ? null : envKey;
    }

    /** https://api.deepseek.com/v1 -> https://api.deepseek.com,余额接口不在 /v1 下 */
    private static String origin(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.strip();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        return value;
    }
}
