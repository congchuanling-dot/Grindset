package com.grindset.backend.dto;

import java.util.List;

/**
 * 掌握度画像响应:summary 按用户全量统计,items 按筛选条件返回。
 */
public record KnowledgeProfileView(
        Summary summary,
        List<StateView> items) {

    public record Summary(
            int total,
            int weakCount,
            int learningCount,
            int masteredCount,
            int averageMastery) {
    }

    public record StateView(
            String knowledgePointId,
            String name,
            String domain,
            int mastery,
            Integer prevMastery,
            String level,
            String trend,
            int practiceCount,
            int correctCount,
            Integer lastScore,
            String lastPracticedAt) {
    }
}
