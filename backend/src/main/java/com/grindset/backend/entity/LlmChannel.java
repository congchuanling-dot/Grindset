package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * llm_channels 表:一个渠道 = 一个厂商 + 一个 base_url(可理解为厂商下的一个账号)。
 * 密钥不在这张表,存在 llm_keys 密文表;余额是账号级的,记在本表。
 */
@TableName("llm_channels")
public class LlmChannel {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 对应 application.yml 里 api-keys 的 key,如 deepseek */
    private String providerId;

    /** 展示名,如 DeepSeek / 通义千问 */
    private String name;

    /** 协议类型,目前只有 OPENAI_COMPAT */
    private String providerType;

    /** OpenAI 兼容根地址,不含 /chat/completions */
    private String baseUrl;

    /** 同一 provider 多渠道时,数字大的优先 */
    private Integer priority;

    /** 0 停用 / 1 启用 */
    private Integer enabled;

    /** 账户余额(账号级,不是 key 级),未知为 null */
    private BigDecimal balance;

    /** 余额币种,如 CNY / USD */
    private String balanceCurrency;

    /** MANUAL 手工维护 / AUTO 定时从厂商接口拉取 */
    private String balanceSource;

    /** 余额最后更新时间 */
    private LocalDateTime balanceUpdatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getBalanceCurrency() {
        return balanceCurrency;
    }

    public void setBalanceCurrency(String balanceCurrency) {
        this.balanceCurrency = balanceCurrency;
    }

    public String getBalanceSource() {
        return balanceSource;
    }

    public void setBalanceSource(String balanceSource) {
        this.balanceSource = balanceSource;
    }

    public LocalDateTime getBalanceUpdatedAt() {
        return balanceUpdatedAt;
    }

    public void setBalanceUpdatedAt(LocalDateTime balanceUpdatedAt) {
        this.balanceUpdatedAt = balanceUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
