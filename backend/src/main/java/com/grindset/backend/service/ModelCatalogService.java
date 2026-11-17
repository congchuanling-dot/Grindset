package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.dto.BalanceInfo;
import com.grindset.backend.dto.ModelInfo;
import com.grindset.backend.dto.ProviderInfo;
import com.grindset.backend.entity.LlmChannel;
import com.grindset.backend.entity.LlmKey;
import com.grindset.backend.entity.LlmModel;
import com.grindset.backend.llm.LlmProperties;
import com.grindset.backend.llm.SecretCipher;
import com.grindset.backend.mapper.LlmChannelMapper;
import com.grindset.backend.mapper.LlmKeyMapper;
import com.grindset.backend.mapper.LlmModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 模型目录:每次请求直接读 llm_channels / llm_models / llm_keys,
 * 聚合出前端下拉框用的厂商列表(模型、余额、可用 key 数)。
 * 暂无缓存,改库立即生效。
 */
@Service
public class ModelCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalogService.class);

    private final LlmProperties properties;
    private final LlmChannelMapper channelMapper;
    private final LlmModelMapper modelMapper;
    private final LlmKeyMapper keyMapper;
    private final SecretCipher cipher;

    public ModelCatalogService(LlmProperties properties, LlmChannelMapper channelMapper,
                               LlmModelMapper modelMapper, LlmKeyMapper keyMapper, SecretCipher cipher) {
        this.properties = properties;
        this.channelMapper = channelMapper;
        this.modelMapper = modelMapper;
        this.keyMapper = keyMapper;
        this.cipher = cipher;
    }

    /** 前端下拉框用:只含可用 key 且有上架模型的厂商 */
    public List<ProviderInfo> listProviders() {
        return load().providers();
    }

    /** 对话时按 provider 取所有候选渠道(按优先级排序),找不到返回空列表 */
    public List<CatalogChannel> findChannels(String providerId) {
        return load().channelsByProvider().getOrDefault(providerId, List.of());
    }

    private Catalog load() {
        List<LlmChannel> channels = channelMapper.selectList(new QueryWrapper<LlmChannel>()
                .eq("enabled", 1)
                .orderByDesc("priority")
                .orderByAsc("id"));
        List<LlmModel> models = modelMapper.selectList(new QueryWrapper<LlmModel>()
                .eq("enabled", 1)
                .orderByAsc("sort")
                .orderByAsc("id"));
        // 按最久未用优先排序,天然实现多 key 轮询
        List<LlmKey> keys = keyMapper.selectList(new QueryWrapper<LlmKey>()
                .eq("status", 0)
                .orderByAsc("last_used_at")
                .orderByAsc("id"));

        Map<Long, List<LlmModel>> modelsByChannel = new LinkedHashMap<>();
        for (LlmModel model : models) {
            modelsByChannel.computeIfAbsent(model.getChannelId(), key -> new ArrayList<>()).add(model);
        }

        Map<Long, List<KeyEntry>> keysByChannel = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (LlmKey key : keys) {
            if (key.getCooldownUntil() != null && key.getCooldownUntil().isAfter(now)) {
                continue;
            }
            keysByChannel.computeIfAbsent(key.getChannelId(), id -> new ArrayList<>())
                    .add(new KeyEntry(key.getId(), cipher.decrypt(key.getKeyCipher(), key.getChannelId())));
        }

        // 同一 provider 可能有多条渠道(多个账号),按优先级聚合到一个下拉框分组里
        Map<String, List<LlmChannel>> channelsByProvider = new LinkedHashMap<>();
        for (LlmChannel channel : channels) {
            channelsByProvider.computeIfAbsent(channel.getProviderId(), id -> new ArrayList<>()).add(channel);
        }

        List<ProviderInfo> providers = new ArrayList<>();
        Map<String, List<CatalogChannel>> runtimeChannels = new LinkedHashMap<>();
        Map<String, String> envKeys = properties.getApiKeys();

        for (Map.Entry<String, List<LlmChannel>> entry : channelsByProvider.entrySet()) {
            String providerId = entry.getKey();
            Map<String, String> modelDisplayNames = new LinkedHashMap<>();
            List<CatalogChannel> candidateChannels = new ArrayList<>();
            int keyCount = 0;
            BigDecimal balanceSum = null;
            String currency = null;
            String balanceSource = null;
            LocalDateTime balanceUpdatedAt = null;
            String providerName = null;

            for (LlmChannel channel : entry.getValue()) {
                List<LlmModel> channelModels = modelsByChannel.get(channel.getId());
                if (channelModels == null || channelModels.isEmpty()) {
                    continue;
                }
                List<KeyEntry> channelKeys = keysByChannel.get(channel.getId());
                if (channelKeys == null || channelKeys.isEmpty()) {
                    // 还没在库里配 key 时,兼容用环境变量里的单 key
                    String envKey = envKeys == null ? null : envKeys.get(providerId);
                    if (envKey == null || envKey.isBlank()) {
                        log.debug("渠道 {} 没有可用 API Key,跳过", providerId);
                        continue;
                    }
                    channelKeys = List.of(new KeyEntry(null, envKey));
                }

                if (providerName == null) {
                    providerName = channel.getName();
                }
                keyCount += channelKeys.size();
                for (LlmModel model : channelModels) {
                    String name = displayName(model);
                    modelDisplayNames.putIfAbsent(model.getModelId(), name);
                }
                candidateChannels.add(new CatalogChannel(providerId, channel.getName(), channel.getBaseUrl(),
                        channelModels.stream().map(LlmModel::getModelId).toList(), channelKeys));

                // 余额是账号级的:多渠道直接累加
                if (channel.getBalance() != null) {
                    balanceSum = balanceSum == null ? channel.getBalance() : balanceSum.add(channel.getBalance());
                    if (currency == null) {
                        currency = channel.getBalanceCurrency();
                    }
                    if (channel.getBalanceUpdatedAt() != null
                            && (balanceUpdatedAt == null || channel.getBalanceUpdatedAt().isAfter(balanceUpdatedAt))) {
                        balanceUpdatedAt = channel.getBalanceUpdatedAt();
                    }
                    if ("AUTO".equalsIgnoreCase(channel.getBalanceSource())) {
                        balanceSource = "AUTO";
                    } else if (balanceSource == null) {
                        balanceSource = "MANUAL";
                    }
                }
            }

            if (candidateChannels.isEmpty()) {
                continue;
            }

            List<ModelInfo> modelInfos = modelDisplayNames.entrySet().stream()
                    .map(item -> new ModelInfo(item.getKey(), item.getValue()))
                    .toList();
            BalanceInfo balanceInfo = balanceSum == null ? null
                    : new BalanceInfo(balanceSum, currency == null ? "CNY" : currency,
                    balanceUpdatedAt == null ? null : balanceUpdatedAt.format(STANDARD), balanceSource);

            providers.add(new ProviderInfo(providerId, providerName, modelInfos, balanceInfo, keyCount));
            runtimeChannels.put(providerId, List.copyOf(candidateChannels));
        }

        log.debug("模型目录已加载:{} 个可用厂商", providers.size());
        return new Catalog(List.copyOf(providers), Map.copyOf(runtimeChannels));
    }

    private static String displayName(LlmModel model) {
        String name = model.getDisplayName();
        return name == null || name.isBlank() ? model.getModelId() : name;
    }

    /** 一次加载结果:前端展示列表 + 对话时按 provider 查渠道 */
    private record Catalog(List<ProviderInfo> providers,
                           Map<String, List<CatalogChannel>> channelsByProvider) {
    }

    /** 一把可用的 key:id 为 null 表示来自环境变量 */
    public record KeyEntry(Long id, String apiKey) {
    }

    /** 一个可用渠道:地址 + 模型白名单 + 候选 key */
    public record CatalogChannel(String providerId, String displayName, String baseUrl,
                                 List<String> modelIds, List<KeyEntry> keys) {

        public boolean supports(String modelId) {
            return modelIds.contains(modelId);
        }
    }
}
