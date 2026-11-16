package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * question_retries 表:一次错题重练记录。
 * 每次提交都新增一条,不覆盖历史;knowledge_points 是本次回写画像使用的知识点。
 */
@TableName(value = "question_retries", autoResultMap = true)
public class QuestionRetry {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long wrongId;

    private Long userId;

    private String answer;

    private Integer score;

    private String comment;

    private String suggestion;

    private String referenceAnswer;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWrongId() {
        return wrongId;
    }

    public void setWrongId(Long wrongId) {
        this.wrongId = wrongId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
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

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<String> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
