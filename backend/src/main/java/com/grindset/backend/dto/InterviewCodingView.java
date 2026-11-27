package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 面试 FREE_RUN 编程题视图:题面 + 通用 main 骨架 + 最近一次运行结果。
 * 不下发测试用例,也不要求函数签名。
 */
public record InterviewCodingView(
        String interviewQuestionId,
        int seq,
        boolean skipped,
        String codingQuestionId,
        String title,
        String content,
        int difficulty,
        String domain,
        List<String> knowledgePoints,
        String intro,
        String deadlineAt,
        Long deadlineAtEpochMs,
        Map<String, String> starterCode,
        String codeDraft,
        String codeLanguage,
        RunView latestRun) {

    /** 控制台运行结果 */
    public record RunView(
            String status,
            String stdout,
            String stderr,
            int exitCode,
            int runtimeMs,
            String ranAt) {
    }
}
