package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * user_kp_mastery_log 表：掌握度每次变化的审计日志。
 * source_type + source_id + source_item_id + kp_key 用于识别重复计算。
 */
@TableName("user_kp_mastery_log")
public class UserKpMasteryLog {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String kpKey;

    private String dimension;

    private Integer beforeScore;

    private Integer afterScore;

    private Integer delta;

    private Integer score;

    /** INTERVIEW / TRAINING */
    private String sourceType;

    private Long sourceId;

    private Long sourceItemId;

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

    public String getKpKey() {
        return kpKey;
    }

    public void setKpKey(String kpKey) {
        this.kpKey = kpKey;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public Integer getBeforeScore() {
        return beforeScore;
    }

    public void setBeforeScore(Integer beforeScore) {
        this.beforeScore = beforeScore;
    }

    public Integer getAfterScore() {
        return afterScore;
    }

    public void setAfterScore(Integer afterScore) {
        this.afterScore = afterScore;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Long getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(Long sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
