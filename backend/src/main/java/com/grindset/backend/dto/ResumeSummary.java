package com.grindset.backend.dto;

/**
 * 简历列表项:不含章节明细,供下拉框和历史列表使用。
 */
public record ResumeSummary(
        String id,
        String fileName,
        String candidateName,
        String candidateType,
        int parseStatus,
        String createdAt) {
}
