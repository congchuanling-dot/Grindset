package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * interview_weakness 表：一次面试暴露出的可训练弱项。
 * kp_key 是归一化后的知识点键，跨场聚合与训练选题都以它为准。
 */
@TableName("interview_weakness")
public class InterviewWeakness {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long questionId;

    private Integer questionSeq;

    private String kpKey;

    private String kpName;

    private String domain;

    private String dimension;

    /** 1 低 / 2 中 / 3 高 */
    private Integer severity;

    private String evidence;

    private String advice;

    /** REPORT / QUESTION / JD */
    private String source;

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

    public Integer getQuestionSeq() {
        return questionSeq;
    }

    public void setQuestionSeq(Integer questionSeq) {
        this.questionSeq = questionSeq;
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

    public Integer getSeverity() {
        return severity;
    }

    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
