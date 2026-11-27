package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.grindset.backend.common.InterviewQuestionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * interview_questions 表:一道大题线程(主问题 + 1~3 次追问)。
 * knowledge_points / evaluation_json 是 JSON 列。
 */
@TableName(value = "interview_questions", autoResultMap = true)
public class InterviewQuestion {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long sessionId;

    private Integer seq;

    /** VERBAL 语音题 / CODING 编程题 */
    private InterviewQuestionType questionType;

    private Long codingQuestionId;

    private String codingTitle;

    /** ACM / LEETCODE */
    private String codingMode;

    private Long codingSubmissionId;

    private String judgeStatus;

    private Integer passed;

    private Integer total;

    /** 最近一次代码草稿,用于刷新恢复与面试官了解当前代码 */
    private String codeDraft;

    private String codeLanguage;

    /** 最近一次运行控制台输出 */
    private String runStdout;

    private String runStderr;

    private Integer runExitCode;

    private Integer runRuntimeMs;

    private LocalDateTime runAt;

    private Integer runCount;

    /** 0 未开始 / 1 提问中 / 2 作答中 / 3 已收束 / 4 已放弃 */
    private Integer status;

    private Integer followUpCount;

    private Integer score;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;

    private String comment;

    private String suggestion;

    private String evaluationJson;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime deadlineAt;

    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public InterviewQuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(InterviewQuestionType questionType) {
        this.questionType = questionType;
    }

    public Long getCodingQuestionId() {
        return codingQuestionId;
    }

    public void setCodingQuestionId(Long codingQuestionId) {
        this.codingQuestionId = codingQuestionId;
    }

    public String getCodingTitle() {
        return codingTitle;
    }

    public void setCodingTitle(String codingTitle) {
        this.codingTitle = codingTitle;
    }

    public String getCodingMode() {
        return codingMode;
    }

    public void setCodingMode(String codingMode) {
        this.codingMode = codingMode;
    }

    public Long getCodingSubmissionId() {
        return codingSubmissionId;
    }

    public void setCodingSubmissionId(Long codingSubmissionId) {
        this.codingSubmissionId = codingSubmissionId;
    }

    public String getJudgeStatus() {
        return judgeStatus;
    }

    public void setJudgeStatus(String judgeStatus) {
        this.judgeStatus = judgeStatus;
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

    public String getCodeDraft() {
        return codeDraft;
    }

    public void setCodeDraft(String codeDraft) {
        this.codeDraft = codeDraft;
    }

    public String getCodeLanguage() {
        return codeLanguage;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    public String getRunStdout() {
        return runStdout;
    }

    public void setRunStdout(String runStdout) {
        this.runStdout = runStdout;
    }

    public String getRunStderr() {
        return runStderr;
    }

    public void setRunStderr(String runStderr) {
        this.runStderr = runStderr;
    }

    public Integer getRunExitCode() {
        return runExitCode;
    }

    public void setRunExitCode(Integer runExitCode) {
        this.runExitCode = runExitCode;
    }

    public Integer getRunRuntimeMs() {
        return runRuntimeMs;
    }

    public void setRunRuntimeMs(Integer runRuntimeMs) {
        this.runRuntimeMs = runRuntimeMs;
    }

    public LocalDateTime getRunAt() {
        return runAt;
    }

    public void setRunAt(LocalDateTime runAt) {
        this.runAt = runAt;
    }

    public Integer getRunCount() {
        return runCount;
    }

    public void setRunCount(Integer runCount) {
        this.runCount = runCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getFollowUpCount() {
        return followUpCount;
    }

    public void setFollowUpCount(Integer followUpCount) {
        this.followUpCount = followUpCount;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<String> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
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

    public String getEvaluationJson() {
        return evaluationJson;
    }

    public void setEvaluationJson(String evaluationJson) {
        this.evaluationJson = evaluationJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
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
