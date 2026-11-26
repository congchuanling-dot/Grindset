package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ai_usage_record 表:每次模型调用的 token 用量与成本。
 * 单价在写入时快照,后续调价不影响历史账单。
 */
@TableName("ai_usage_record")
public class AiUsageRecord {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    /** 业务类型,取 AiBizType.name() */
    private String bizType;

    /** 业务主键:会话 ID / 面试 ID / 简历 ID */
    private String bizRef;

    private String providerId;

    private String modelId;

    private Long keyId;

    private Integer inputTokens;

    private Integer outputTokens;

    /** 输入中命中缓存的 token */
    private Integer cachedTokens;

    /** 1=token 为字符估算值 */
    private Integer estimated;

    /** 单价快照,元/千 token */
    private BigDecimal inputPrice;

    private BigDecimal cachedInputPrice;

    private BigDecimal outputPrice;

    /** 本次成本,元 */
    private BigDecimal cost;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizRef() {
        return bizRef;
    }

    public void setBizRef(String bizRef) {
        this.bizRef = bizRef;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Long getKeyId() {
        return keyId;
    }

    public void setKeyId(Long keyId) {
        this.keyId = keyId;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getCachedTokens() {
        return cachedTokens;
    }

    public void setCachedTokens(Integer cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    public Integer getEstimated() {
        return estimated;
    }

    public void setEstimated(Integer estimated) {
        this.estimated = estimated;
    }

    public BigDecimal getInputPrice() {
        return inputPrice;
    }

    public void setInputPrice(BigDecimal inputPrice) {
        this.inputPrice = inputPrice;
    }

    public BigDecimal getCachedInputPrice() {
        return cachedInputPrice;
    }

    public void setCachedInputPrice(BigDecimal cachedInputPrice) {
        this.cachedInputPrice = cachedInputPrice;
    }

    public BigDecimal getOutputPrice() {
        return outputPrice;
    }

    public void setOutputPrice(BigDecimal outputPrice) {
        this.outputPrice = outputPrice;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
