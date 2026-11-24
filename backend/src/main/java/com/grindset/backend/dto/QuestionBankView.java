package com.grindset.backend.dto;

import java.util.List;

/**
 * 题库列表响应:summary 按用户全量统计,items 按筛选条件分页返回。
 * status 取值 TODO 未做 / DONE 已做 / WRONG 错题。
 */
public record QuestionBankView(
        Summary summary,
        int filteredTotal,
        int page,
        int size,
        List<Item> items) {

    public record Summary(
            int total,
            int done,
            int wrong,
            int todo,
            int totalAttempts) {
    }

    public record Item(
            String id,
            String title,
            String preview,
            String type,
            String judgeMode,
            String codeMode,
            int difficulty,
            String domain,
            List<String> knowledgePoints,
            String status,
            int attemptCount,
            Integer bestScore,
            Integer lastScore,
            String lastAttemptAt) {
    }
}
