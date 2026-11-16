package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * wrong_questions 表:收束时得分 <= 2 的题目快照。
 * 快照独立于原面试,原会话删除后仍可重练;knowledge_points / kp_ids 是 JSON 列。
 */
@TableName(value = "wrong_questions", autoResultMap = true)
public class WrongQuestion {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long questionId;

    private String position;

    private String round;

    private String questionContent;

    private String questionContext;

    private String originalAnswer;

    private Integer originalScore;

    private String originalComment;

    private String originalSuggestion;

    private String originalFeedback;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> kpIds;

    /** 0 待重练 / 1 已掌握 */
    private Integer status;

    private Integer retryCount;

    private Integer bestScore;

    private Integer lastScore;

    private LocalDateTime lastRetryAt;

    private LocalDateTime nextReviewAt;

    /** 状态回退为待重练时要把该字段清空,因此更新时始终写入(null 也写) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime masteredAt;

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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public String getQuestionContent() {
        return questionContent;
    }

    public void setQuestionContent(String questionContent) {
        this.questionContent = questionContent;
    }

    public String getQuestionContext() {
        return questionContext;
    }

    public void setQuestionContext(String questionContext) {
        this.questionContext = questionContext;
    }

    public String getOriginalAnswer() {
        return originalAnswer;
    }

    public void setOriginalAnswer(String originalAnswer) {
        this.originalAnswer = originalAnswer;
    }

    public Integer getOriginalScore() {
        return originalScore;
    }

    public void setOriginalScore(Integer originalScore) {
        this.originalScore = originalScore;
    }

    public String getOriginalComment() {
        return originalComment;
    }

    public void setOriginalComment(String originalComment) {
        this.originalComment = originalComment;
    }

    public String getOriginalSuggestion() {
        return originalSuggestion;
    }

    public void setOriginalSuggestion(String originalSuggestion) {
        this.originalSuggestion = originalSuggestion;
    }

    public String getOriginalFeedback() {
        return originalFeedback;
    }

    public void setOriginalFeedback(String originalFeedback) {
        this.originalFeedback = originalFeedback;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<String> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public List<Long> getKpIds() {
        return kpIds;
    }

    public void setKpIds(List<Long> kpIds) {
        this.kpIds = kpIds;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getBestScore() {
        return bestScore;
    }

    public void setBestScore(Integer bestScore) {
        this.bestScore = bestScore;
    }

    public Integer getLastScore() {
        return lastScore;
    }

    public void setLastScore(Integer lastScore) {
        this.lastScore = lastScore;
    }

    public LocalDateTime getLastRetryAt() {
        return lastRetryAt;
    }

    public void setLastRetryAt(LocalDateTime lastRetryAt) {
        this.lastRetryAt = lastRetryAt;
    }

    public LocalDateTime getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public LocalDateTime getMasteredAt() {
        return masteredAt;
    }

    public void setMasteredAt(LocalDateTime masteredAt) {
        this.masteredAt = masteredAt;
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
