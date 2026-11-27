package com.grindset.backend.dto;

import java.util.List;

/**
 * 创建面试会话请求。resumeId 用字符串接收,避免前端 JS Number 精度丢失。
 */
public class CreateInterviewRequest {

    private String resumeId;

    private String position;

    /** 目标公司,可选 */
    private String company;

    /** intern / campus / experienced / senior,可选;为空时按简历推断 */
    private String jobLevel;

    /** 粘贴的岗位 JD,可选 */
    private String jdText;

    /** 前端预览后回传的岗位画像,可选;带这里就不再重复解析 JD */
    private JdProfile jdProfile;

    private String style;

    private String round;

    private List<String> focus;

    /** 3 / 5 / 8 */
    private Integer questionCount;

    /** 最后一题是否手撕代码 */
    private Boolean codingEnabled;

    /** 编程环节时限(秒),默认 1800 */
    private Integer codingTimeLimitSec;

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
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

    public JdProfile getJdProfile() {
        return jdProfile;
    }

    public void setJdProfile(JdProfile jdProfile) {
        this.jdProfile = jdProfile;
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
