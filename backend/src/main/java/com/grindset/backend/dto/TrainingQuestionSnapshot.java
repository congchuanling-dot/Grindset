package com.grindset.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 错题快照：保证题库题下架或 AI 题仍可重练。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingQuestionSnapshot(
        String title,
        String content,
        List<String> keyPoints,
        String referenceAnswer,
        Integer difficulty) {
}
