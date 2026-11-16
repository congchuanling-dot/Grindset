package com.grindset.backend.dto;

import java.util.List;

/**
 * 创建面试会话请求。resumeId 用字符串接收,避免前端 JS Number 精度丢失。
 */
public class CreateInterviewRequest {

    private String resumeId;

    private String position;

    private String style;

    private String round;

    private List<String> focus;

    /** 3 / 5 / 8 */
    private Integer questionCount;

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
}
