package com.grindset.backend.dto;

/**
 * 代码提交记录/判题结果。
 * 隐藏用例只回传通过数,不回传输入与期望输出。
 */
public record CodingSubmissionView(
        String id,
        String questionId,
        String language,
        String code,
        String status,
        int passed,
        int total,
        Integer runtimeMs,
        String errorMessage,
        String createdAt,
        String finishedAt) {
}
