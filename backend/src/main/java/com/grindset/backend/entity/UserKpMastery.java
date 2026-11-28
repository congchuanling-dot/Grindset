package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * user_kp_mastery 表：用户对某个知识点的长期掌握估计。
 * 一个用户一个 kp_key 只有一行，掌握度由作答分数通过 EMA 更新。
 */
@TableName("user_kp_mastery")
public class UserKpMastery {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String kpKey;

    private String kpName;

    private String domain;

    private String dimension;

    /** 0~100 */
    private Integer mastery;

    /** WEAK / LEARNING / MASTERED */
    private String level;

    private Integer evidenceCount;

    private Integer lastScore;

    private String lastSource;

    private Integer lastBefore;

    private Integer lastDelta;

    private Integer consecutiveGood;

    private LocalDateTime lastPracticedAt;

    private LocalDateTime nextReviewAt;

    private LocalDateTime eliminatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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

    public String getKpKey() {
        return kpKey;
    }

    public void setKpKey(String kpKey) {
        this.kpKey = kpKey;
    }

    public String getKpName() {
        return kpName;
    }

    public void setKpName(String kpName) {
        this.kpName = kpName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public Integer getMastery() {
        return mastery;
    }

    public void setMastery(Integer mastery) {
        this.mastery = mastery;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Integer getLastScore() {
        return lastScore;
    }

    public void setLastScore(Integer lastScore) {
        this.lastScore = lastScore;
    }

    public String getLastSource() {
        return lastSource;
    }

    public void setLastSource(String lastSource) {
        this.lastSource = lastSource;
    }

    public Integer getLastBefore() {
        return lastBefore;
    }

    public void setLastBefore(Integer lastBefore) {
        this.lastBefore = lastBefore;
    }

    public Integer getLastDelta() {
        return lastDelta;
    }

    public void setLastDelta(Integer lastDelta) {
        this.lastDelta = lastDelta;
    }

    public Integer getConsecutiveGood() {
        return consecutiveGood;
    }

    public void setConsecutiveGood(Integer consecutiveGood) {
        this.consecutiveGood = consecutiveGood;
    }

    public LocalDateTime getLastPracticedAt() {
        return lastPracticedAt;
    }

    public void setLastPracticedAt(LocalDateTime lastPracticedAt) {
        this.lastPracticedAt = lastPracticedAt;
    }

    public LocalDateTime getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public LocalDateTime getEliminatedAt() {
        return eliminatedAt;
    }

    public void setEliminatedAt(LocalDateTime eliminatedAt) {
        this.eliminatedAt = eliminatedAt;
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
