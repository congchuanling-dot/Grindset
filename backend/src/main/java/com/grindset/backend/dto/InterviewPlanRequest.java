package com.grindset.backend.dto;

import java.util.List;

/**
 * 面试计划预览请求。字段与创建面试基本一致,额外带 mode / durationMin。
 * 不创建会话,只解析 JD 并返回推荐计划。
 */
public class InterviewPlanRequest {

    private String resumeId;

    /** quick / jd / drill */
    private String mode;

    private String position;

    private String company;

    private String jobLevel;

    private String jdText;

    private String style;

    private String round;

    /** 15 / 25 / 40 */
    private Integer durationMin;

    /** 3 / 5 / 8 */
    private Integer questionCount;

    private List<String> focus;

    private Boolean codingEnabled;

    private Integer codingTimeLimitSec;

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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

    public String getJobLevel() {
        return jobLevel;
    }

    public void setJobLevel(String jobLevel) {
        this.jobLevel = jobLevel;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public List<String> getFocus() {
        return focus;
    }

    public void setFocus(List<String> focus) {
        this.focus = focus;
    }

    public Boolean getCodingEnabled() {
        return codingEnabled;
    }

    public void setCodingEnabled(Boolean codingEnabled) {
        this.codingEnabled = codingEnabled;
    }

    public Integer getCodingTimeLimitSec() {
        return codingTimeLimitSec;
    }

    public void setCodingTimeLimitSec(Integer codingTimeLimitSec) {
        this.codingTimeLimitSec = codingTimeLimitSec;
    }
}
