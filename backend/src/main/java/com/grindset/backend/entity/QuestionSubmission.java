package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.grindset.backend.common.SubmissionScene;
import com.grindset.backend.common.constant.JudgeLanguage;
import com.grindset.backend.common.constant.SubmissionStatus;

import java.time.LocalDateTime;

/**
 * question_submissions 表:一次代码提交与判题结果。
 * status 取值见 CodingStatus;提交先落库为 PENDING,由判题线程更新为最终状态。
 */
@TableName("question_submissions")
public class QuestionSubmission {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long questionId;

    /** BANK 题库 / INTERVIEW 面试 */
    private SubmissionScene scene;

    private Long interviewSessionId;

    private Long interviewQuestionId;

    /** JAVA / PYTHON */
    private JudgeLanguage language;

    private String code;

    /** PENDING / RUNNING / ACCEPTED / WRONG_ANSWER / COMPILE_ERROR / RUNTIME_ERROR / TIME_LIMIT_EXCEEDED / SYSTEM_ERROR */
    private SubmissionStatus status;

    private Integer passed;

    private Integer total;

    private Integer runtimeMs;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

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

    public SubmissionScene getScene() {
        return scene;
    }

    public void setScene(SubmissionScene scene) {
        this.scene = scene;
    }

    public Long getInterviewSessionId() {
        return interviewSessionId;
    }

    public void setInterviewSessionId(Long interviewSessionId) {
        this.interviewSessionId = interviewSessionId;
    }

    public Long getInterviewQuestionId() {
        return interviewQuestionId;
    }

    public void setInterviewQuestionId(Long interviewQuestionId) {
        this.interviewQuestionId = interviewQuestionId;
    }

    public JudgeLanguage getLanguage() {
        return language;
    }

    public void setLanguage(JudgeLanguage language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public Integer getPassed() {
        return passed;
    }

    public void setPassed(Integer passed) {
        this.passed = passed;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getRuntimeMs() {
        return runtimeMs;
    }

    public void setRuntimeMs(Integer runtimeMs) {
        this.runtimeMs = runtimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
