package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 编程题详情:题面、两种模式的初始代码与例题。
 * 每道题同时支持 ACM(标准输入输出)与 LEETCODE(函数签名),隐藏用例不下发。
 */
public record CodingDetail(
        String id,
        String title,
        String content,
        String type,
        int difficulty,
        String domain,
        List<String> knowledgePoints,
        List<String> codeModes,
        Map<String, String> starterCode,
        Map<String, String> starterCodeFunction,
        int timeLimitMs,
        int memoryLimitMb,
        Map<String, List<SampleCase>> samples,
        String status,
        int submissionCount,
        boolean accepted,
        String functionName,
        List<String> paramTypes,
        String returnType) {

    public record SampleCase(
            String input,
            String expectedOutput,
            int sort) {
    }
}
