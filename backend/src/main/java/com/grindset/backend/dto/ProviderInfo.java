package com.grindset.backend.dto;

import java.util.List;

/**
 * 下发给前端的可用模型服务。只包含有可用 key 且有上架模型的厂商,
 * 不含 baseUrl 与 apiKey,密钥绝不出服务端。
 */
public record ProviderInfo(String id, String name, List<ModelInfo> models,
                           BalanceInfo balance, int keyCount) {
}
