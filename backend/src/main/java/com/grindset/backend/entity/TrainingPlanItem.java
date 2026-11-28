package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * training_plan_item 表：训练计划里的一道题。
 * 题库题用 question_id 引用；AI 生成题把内容放在 generated_json，作答结果直接落本表。
 */
@TableName(value = "training_plan_item", autoResultMap = true)
public class TrainingPlanItem {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long planId;

    private Long userId;

    private Integer seq;

    /** PRACTICE / RETEST */
    private String itemRole;

    private String kpKey;

    private String kpName;

    private String dimension;

    /** TEXT / CODING */
    private String itemType;

    private Integer difficulty;

    private Long questionId;

    /** AI 生成题 JSON 字符串 */
    private String generatedJson;

    /** 0 未做 / 1 已完成 */
    private Integer status;

    private Integer score;

    private Long attemptId;

    private Long mistakeId;

    private String answerText;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hitPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> missedPoints;

    private String comment;

    private String suggestion;

    private String feedbackJson;

    private String similarJson;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getItemRole() {
        return itemRole;
    }

    public void setItemRole(String itemRole) {
        this.itemRole = itemRole;
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

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getGeneratedJson() {
        return generatedJson;
    }

    public void setGeneratedJson(String generatedJson) {
        this.generatedJson = generatedJson;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public Long getMistakeId() {
        return mistakeId;
    }

    public void setMistakeId(Long mistakeId) {
        this.mistakeId = mistakeId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public List<String> getHitPoints() {
        return hitPoints;
    }

    public void setHitPoints(List<String> hitPoints) {
        this.hitPoints = hitPoints;
    }

    public List<String> getMissedPoints() {
        return missedPoints;
    }

    public void setMissedPoints(List<String> missedPoints) {
        this.missedPoints = missedPoints;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getFeedbackJson() {
        return feedbackJson;
    }

    public void setFeedbackJson(String feedbackJson) {
        this.feedbackJson = feedbackJson;
    }

    public String getSimilarJson() {
        return similarJson;
    }

    public void setSimilarJson(String similarJson) {
        this.similarJson = similarJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
