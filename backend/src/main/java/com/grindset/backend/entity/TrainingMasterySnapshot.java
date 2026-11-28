package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * training_mastery_snapshot 表：每日总体掌握度与五维快照，用于成长曲线。
 */
@TableName("training_mastery_snapshot")
public class TrainingMasterySnapshot {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private LocalDate snapshotDate;

    private Integer overallMastery;

    private String dimensionJson;

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

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public Integer getOverallMastery() {
        return overallMastery;
    }

    public void setOverallMastery(Integer overallMastery) {
        this.overallMastery = overallMastery;
    }

    public String getDimensionJson() {
        return dimensionJson;
    }

    public void setDimensionJson(String dimensionJson) {
        this.dimensionJson = dimensionJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
