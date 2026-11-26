package com.grindset.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户钱包查询结果。大 ID 与金额都按前端可安全解析的类型输出。
 */
public final class WalletDtos {

    private WalletDtos() {
    }

    /** 钱包概览 */
    public record Info(BigDecimal balance, String currency, String updatedAt) {
    }

    /** 单条余额流水 */
    public record LogView(String id, BigDecimal changeAmount, BigDecimal balanceAfter,
                          String changeType, String changeLabel,
                          String bizType, String bizLabel, String bizRef,
                          String remark, String createdAt) {
    }

    /** 流水分页 */
    public record LogPage(List<LogView> items, long total, int page, int size) {
    }
}
