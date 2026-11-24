package com.grindset.backend.dto;

import java.util.List;

/**
 * 题库题目详情。首次作答前不返回参考答案,避免直接抄。
 */
public record QuestionBankDetail(
        String id,
        int seq,
        String title,
        String content,
        String type,
        String judgeMode,
        int difficulty,
        String domain,
        List<String> knowledgePoints,
        List<String> keyPoints,
        String referenceAnswer,
        String status,
        int attemptCount,
        Integer bestScore,
        Integer lastScore,
        String lastAttemptAt,
        List<AttemptView> attempts) {

    public record AttemptView(
            String id,
            String answer,
            int score,
            List<String> hitPoints,
            List<String> missedPoints,
            String comment,
            String suggestion,
            String createdAt) {
    }
}
