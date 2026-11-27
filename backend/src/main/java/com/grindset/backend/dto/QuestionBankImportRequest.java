package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 管理端批量导入题库的请求体。content_hash 由服务端按题干计算,重复自动跳过。
 * 问答题只填基础字段;编程题额外填 main 骨架与时限,不再需要测试用例。
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
            Integer timeLimitMs,
            Integer memoryLimitMb,
            Map<String, String> starterCode) {
    }
}
