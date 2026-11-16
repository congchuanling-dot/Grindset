package com.grindset.backend.dto;

import java.util.List;

/**
 * 错题本列表响应:summary 按用户全量统计,items 按筛选条件返回。
 */
public record WrongQuestionView(
        Summary summary,
        int filteredTotal,
        List<Item> items) {

    public record Summary(
            int total,
            int pendingCount,
            int masteredCount,
            int dueCount) {
    }

    public record Item(
            String id,
            String questionPreview,
            List<String> knowledgePoints,
            Integer originalScore,
            String status,
            int retryCount,
            Integer bestScore,
            Integer lastScore,
            String lastRetryAt,
            String nextReviewAt,
            String createdAt,
            String sessionId) {
    }
}
