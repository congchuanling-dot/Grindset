package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 管理端批量导入题库的请求体。content_hash 由服务端按题干计算,重复自动跳过。
 * ;问答型题目只填基础字段编程题额外填判题字段与用例。
 */
public record QuestionBankImportRequest(List<Item> questions) {

    public record Item(
            String title,
            String content,
            String type,
            Integer difficulty,
            String domain,
            String referenceAnswer,
            List<String> keyPoints,
            List<String> kpNames,
            String source,
            String sourceUrl,
            String judgeMode,
            String codeMode,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            String functionName,
            List<String> paramTypes,
            String returnType,
            Map<String, String> starterCode,
            Map<String, String> starterCodeFunction,
            List<TestCaseItem> testCases) {
    }

    public record TestCaseItem(
            String caseMode,
            String input,
            String expectedOutput,
            Integer isSample,
            Integer sort) {
    }
}
