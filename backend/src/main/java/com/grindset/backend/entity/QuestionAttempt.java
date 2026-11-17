package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * question_attempts 表:一次题库作答记录。
 * hit_points / missed_points 是本次 AI 评分命中和遗漏的要点。
 */
@TableName(value = "question_attempts", autoResultMap = true)
public class QuestionAttempt {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long questionId;

    private String answer;

    private Integer score;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hitPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> missedPoints;

    private String comment;

    private String suggestion;

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

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
