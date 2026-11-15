package com.grindset.backend.dto;

import java.math.BigDecimal;

/**
 * 渠道余额,给前端展示用。amount 为 null 表示还没维护过。
 * source:MANUAL 手工维护 / AUTO 定时从厂商接口拉取。
 */
public record BalanceInfo(BigDecimal amount, String currency, String updatedAt, String source) {
}
