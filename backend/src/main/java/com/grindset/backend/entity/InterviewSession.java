package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.grindset.backend.common.InterviewStyle;
import com.grindset.backend.common.JobLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * interview_sessions 表:一次 AI 面试会话。
 * focus / report_json 是 JSON 列;report_json 直接存原始字符串,读写都交给 Jackson。
 */
@TableName(value = "interview_sessions", autoResultMap = true)
public class InterviewSession {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long resumeId;

    private String position;

    /** 目标公司,可为空 */
    private String company;

    /** 岗位级别,可为空 */
    private JobLevel jobLevel;

    /** JD 原文,最多 8000 字 */
    private String jdText;

    /** JD 解析后的岗位画像 JSON;为空表示未启用或解析失败 */
    private String jdProfileJson;

    /** pressure / middle / relaxed */
    private InterviewStyle style;

    private String round;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> focus;

    private Integer questionCount;

    /** 1 最后一题手撕代码 / 0 不启用 */
    private Integer codingEnabled;

    /** 编程环节时限(秒) */
    private Integer codingTimeLimitSec;

    private Integer completedQuestions;

    /** 0 进行中 / 1 已结束 */
    private Integer status;

    private Integer overallScore;

    private String reportJson;

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

    public Long getResumeId() {
        return resumeId;
    }

    public void setResumeId(Long resumeId) {
        this.resumeId = resumeId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public JobLevel getJobLevel() {
        return jobLevel;
    }

    public void setJobLevel(JobLevel jobLevel) {
        this.jobLevel = jobLevel;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getJdProfileJson() {
        return jdProfileJson;
    }

    public void setJdProfileJson(String jdProfileJson) {
        this.jdProfileJson = jdProfileJson;
    }

    public InterviewStyle getStyle() {
        return style;
    }

    public void setStyle(InterviewStyle style) {
        this.style = style;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public List<String> getFocus() {
        return focus;
    }

    public void setFocus(List<String> focus) {
        this.focus = focus;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Integer getCodingEnabled() {
        return codingEnabled;
    }

    public void setCodingEnabled(Integer codingEnabled) {
        this.codingEnabled = codingEnabled;
    }

    public Integer getCodingTimeLimitSec() {
        return codingTimeLimitSec;
    }

    public void setCodingTimeLimitSec(Integer codingTimeLimitSec) {
        this.codingTimeLimitSec = codingTimeLimitSec;
    }

    public Integer getCompletedQuestions() {
        return completedQuestions;
    }

    public void setCompletedQuestions(Integer completedQuestions) {
        this.completedQuestions = completedQuestions;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
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
