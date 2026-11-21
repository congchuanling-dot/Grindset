package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.grindset.backend.common.CandidateType;
import com.grindset.backend.common.ResumeFileType;

import java.time.LocalDateTime;

/**
 * resumes 表:一次简历上传对应一条记录。
 * 解析结果落子表;原件存阿里云 OSS 私有桶,这里只保存 object key。
 */
@TableName("resumes")
public class Resume {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String fileName;

    /** PDF / DOCX / MD / TXT */
    private ResumeFileType fileType;

    private Integer fileSize;

    /** 阿里云 OSS 对象 key;未配置 OSS 或上传失败时为空 */
    private String originalObjectKey;

    private String candidateName;

    /** FRESH_GRADUATE / INTERN / EXPERIENCED / UNKNOWN */
    private CandidateType candidateType;

    /** 0 解析中 / 1 成功 / 2 失败 */
    private Integer parseStatus;

    /** 已尝试解析次数,用于异步重试和补偿 */
    private Integer parseAttempt;

    private LocalDateTime parseStartedAt;

    private LocalDateTime parseFinishedAt;

    private String errorMsg;

    /** 解析使用的模型 ID */
    private String model;

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ResumeFileType getFileType() {
        return fileType;
    }

    public void setFileType(ResumeFileType fileType) {
        this.fileType = fileType;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
    }

    public String getOriginalObjectKey() {
        return originalObjectKey;
    }

    public void setOriginalObjectKey(String originalObjectKey) {
        this.originalObjectKey = originalObjectKey;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public CandidateType getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(CandidateType candidateType) {
        this.candidateType = candidateType;
    }

    public Integer getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(Integer parseStatus) {
        this.parseStatus = parseStatus;
    }

    public Integer getParseAttempt() {
        return parseAttempt;
    }

    public void setParseAttempt(Integer parseAttempt) {
        this.parseAttempt = parseAttempt;
    }

    public LocalDateTime getParseStartedAt() {
        return parseStartedAt;
    }

    public void setParseStartedAt(LocalDateTime parseStartedAt) {
        this.parseStartedAt = parseStartedAt;
    }

    public LocalDateTime getParseFinishedAt() {
        return parseFinishedAt;
    }

    public void setParseFinishedAt(LocalDateTime parseFinishedAt) {
        this.parseFinishedAt = parseFinishedAt;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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
