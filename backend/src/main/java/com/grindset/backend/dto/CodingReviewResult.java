package com.grindset.backend.dto;

import java.util.List;

/**
 * 题库编程题的 AI 点评:运行结果 + 评分 + 已处理/遗漏的边界。
 * runStatus 表示程序是否跑完,progressStatus 表示这道题的做题状态 TODO / DONE / WRONG。
 */
public record CodingReviewResult(
        String runStatus,
        String stdout,
        String stderr,
        int exitCode,
        int runtimeMs,
        int score,
        List<String> hitPoints,
        List<String> missedPoints,
        String comment,
        String suggestion,
        String progressStatus,
        int attemptCount,
        Integer bestScore,
        String createdAt) {
}
