package com.grindset.backend.dto;

import java.util.List;

/**
 * 管理端批量导入题库的请求体。content_hash 由服务端按题干计算,重复自动跳过。
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
            String sourceUrl) {
    }
}
