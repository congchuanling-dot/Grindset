package com.grindset.backend.dto;

import java.util.List;

/**
 * 简历详情响应。雪花 ID 按字符串下发,避免 JS Number 精度丢失。
 */
public record ResumeDetail(
        String id,
        String fileName,
        String fileType,
        long fileSize,
        boolean hasOriginal,
        String originalUrl,
        String originalDownloadUrl,
        int parseStatus,
        String errorMsg,
        String candidateName,
        String candidateType,
        String model,
        String createdAt,
        List<SkillView> skills,
        List<SectionView> sections) {

    public record SkillView(String id, String name, String category, Integer proficiency, String evidence) {
    }

    public record SectionView(String id, String type, String title, List<ItemView> items) {
    }

    public record ItemView(String id, String title, String subtitle, String startDate, String endDate,
                           String description, List<String> tags) {
    }
}
