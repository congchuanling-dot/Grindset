package com.grindset.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 训练题的详细批改结果。
 *
 * <p>questionType 为 CONCEPT/PRINCIPLE/CODING/DESIGN/ESSAY；
 * scoringPoints 是可供 AI 阅卷的评分点；referenceAnswer 是按题型结构组织的标准答案。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingEvaluation(
        String questionType,
        Integer score,
        List<String> hitPoints,
        List<String> missedPoints,
        String summary,
        String detailedFeedback,
        List<ScoringPoint> scoringPoints,
        List<String> improvements,
        String referenceAnswer) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoringPoint(
            String point,
            Integer weight,
            Boolean required,
            String acceptable) {
    }
}
