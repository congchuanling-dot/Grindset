package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.grindset.backend.common.JudgeMode;
import com.grindset.backend.common.QuestionSource;
import com.grindset.backend.common.QuestionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * question_bank 表:题库内容。
 * type 为 BASICS / SCENARIO / PROJECT / CODE;
 * judge_mode 为 TEXT 问答 / CODING 编程;starter_code 是 JSON 列（语言 -> 初始代码）。
 */
@TableName(value = "question_bank", autoResultMap = true)
public class QuestionBank {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String title;

    private String content;

    /** 面试 FREE_RUN 专用题面:直接给出具体输入和期望输出 */
    private String freeContent;

    /** BASICS 八股 / SCENARIO 场景 / PROJECT 项目 / CODE 编程 */
    private QuestionType type;

    /** TEXT 问答 / CODING 编程 */
    private JudgeMode judgeMode;

    /** 难度 1~3 */
    private Integer difficulty;

    /** 单用例运行时限(毫秒) */
    private Integer timeLimitMs;

    /** 运行内存限制(MB) */
    private Integer memoryLimitMb;

    private String domain;

    private String referenceAnswer;

    /** 各语言初始代码 {"JAVA":"...","PYTHON":"..."} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> starterCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> kpIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> kpNames;

    /** MANUAL / AI / PDF / CRAWL */
    private QuestionSource source;

    private String sourceUrl;

    /** 力扣题号；剑指 Offer 题没有数字题号时为空 */
    private Integer leetcodeNo;

    /** 力扣题目 slug，用于拼原题链接 */
    private String leetcodeSlug;

    /** 题单标签：HOT100 / JIANZHI / CODETOP / HIGH_FREQ / BASE */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** CodeTop 公司分类 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> companies;

    /** CodeTop 总频度，越大越常考 */
    private Integer hotScore;

    /** CodeTop 最近考察日期 */
    private LocalDate lastAskedAt;

    private String contentHash;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFreeContent() {
        return freeContent;
    }

    public void setFreeContent(String freeContent) {
        this.freeContent = freeContent;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public JudgeMode getJudgeMode() {
        return judgeMode;
    }

    public void setJudgeMode(JudgeMode judgeMode) {
        this.judgeMode = judgeMode;
    }

    public Integer getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(Integer timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public Integer getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public void setMemoryLimitMb(Integer memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public Map<String, String> getStarterCode() {
        return starterCode;
    }

    public void setStarterCode(Map<String, String> starterCode) {
        this.starterCode = starterCode;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }

    public List<String> getKeyPoints() {
        return keyPoints;
    }

    public void setKeyPoints(List<String> keyPoints) {
        this.keyPoints = keyPoints;
    }

    public List<Long> getKpIds() {
        return kpIds;
    }

    public void setKpIds(List<Long> kpIds) {
        this.kpIds = kpIds;
    }

    public List<String> getKpNames() {
        return kpNames;
    }

    public void setKpNames(List<String> kpNames) {
        this.kpNames = kpNames;
    }

    public QuestionSource getSource() {
        return source;
    }

    public void setSource(QuestionSource source) {
        this.source = source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Integer getLeetcodeNo() {
        return leetcodeNo;
    }

    public void setLeetcodeNo(Integer leetcodeNo) {
        this.leetcodeNo = leetcodeNo;
    }

    public String getLeetcodeSlug() {
        return leetcodeSlug;
    }

    public void setLeetcodeSlug(String leetcodeSlug) {
        this.leetcodeSlug = leetcodeSlug;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getCompanies() {
        return companies;
    }

    public void setCompanies(List<String> companies) {
        this.companies = companies;
    }

    public Integer getHotScore() {
        return hotScore;
    }

    public void setHotScore(Integer hotScore) {
        this.hotScore = hotScore;
    }

    public LocalDate getLastAskedAt() {
        return lastAskedAt;
    }

    public void setLastAskedAt(LocalDate lastAskedAt) {
        this.lastAskedAt = lastAskedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
