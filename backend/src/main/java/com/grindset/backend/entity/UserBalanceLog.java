package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * user_balance_log 表:每次余额变动一条流水,用于展示和对账。
 * change_amount 正数入账、负数消费。
 */
@TableName("user_balance_log")
public class UserBalanceLog {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private BigDecimal changeAmount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    /** INIT / RECHARGE / CONSUME / ADJUST,取 WalletChangeType.name() */
    private String changeType;

    /** 业务类型,取 AiBizType.name();初始化/调账时为 null */
    private String bizType;

    private String bizRef;

    private String remark;

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

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(BigDecimal balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
