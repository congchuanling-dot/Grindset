package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/** 复盘报告。字段与模型输出的 JSON 一一对应,缺失时在服务层兜底。 */
public record InterviewReport(
        Integer overallScore,
        Map<String, Integer> dimensionScores,
        List<String> weakPoints,
        List<String> highlights,
        List<String> advice,
        List<Coverage> jdCoverage,
        List<String> jdGaps,
        List<Weakness> weaknesses) {

    /** 单个 JD 能力项的覆盖情况,evidence 为候选人原话依据 */
    public record Coverage(
            String name,
            Integer weight,
            Integer score,
            String evidence) {
    }

    /** 结构化弱项:name 必须能对齐到某道题的知识点,questionSeq 用于回溯证据 */
    public record Weakness(
            String name,
            Integer questionSeq,
            Integer severity,
            String evidence,
            String advice,
            String suggestedMode) {
    }
}
