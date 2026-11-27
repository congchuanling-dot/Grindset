package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 编程题详情:题面、通用 main 骨架与最近一次 AI 点评。
 * 平台不提供测试用例,由用户自己构造输入并运行。
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
        String status) {
}
