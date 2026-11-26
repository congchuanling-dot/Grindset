package com.grindset.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Token 用量查询结果。所有大 ID 与金额都按前端可安全解析的类型输出。
 */
public final class UsageDtos {

    private UsageDtos() {
    }

    /** 一个时间范围内的汇总 + 分组 */
    public record Summary(long calls, long inputTokens, long outputTokens, long totalTokens,
                          BigDecimal cost, List<BizUsage> byBizType,
                          List<ModelUsage> byModel, List<DailyUsage> daily) {
    }

    /** 按业务类型(面试出题 / AI 助手 / 简历解析…)分组 */
    public record BizUsage(String bizType, String label, long calls,
                           long inputTokens, long outputTokens, BigDecimal cost) {
    }

    /** 按模型分组 */
    public record ModelUsage(String providerId, String model, long calls,
                             long inputTokens, long outputTokens, BigDecimal cost) {
    }

    /** 按天分组 */
    public record DailyUsage(String date, long inputTokens, long outputTokens, BigDecimal cost) {
    }

    /** 单条调用记录 */
    public record RecordView(String id, String bizType, String bizLabel, String providerId,
                             String model, int inputTokens, int outputTokens, int cachedTokens,
                             boolean estimated, BigDecimal cost, String createdAt) {
    }

    /** 明细分页 */
    public record RecordPage(List<RecordView> items, long total, int page, int size) {
    }
}
