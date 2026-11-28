package com.grindset.backend.dto;

import java.util.List;

/** AI 生成题的批量返回结构：{"questions":[...]}。 */
public record GeneratedQuestionBatch(List<GeneratedQuestion> questions) {
}
