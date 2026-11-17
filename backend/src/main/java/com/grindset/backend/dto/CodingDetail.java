package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 编程题详情:题面、各语言初始代码与例题用例。
 * 隐藏用例不下发,只在判题时使用。
 */
public record CodingDetail(
        String id,
        String title,
        String content,
        String type,
        int difficulty,
        String domain,
        List<String> knowledgePoints,
        Map<String, String> starterCode,
        int timeLimitMs,
        int memoryLimitMb,
        List<SampleCase> samples,
        String status,
        int submissionCount,
        boolean accepted) {

    public record SampleCase(
            String input,
            String expectedOutput,
            int sort) {
    }
}
