package com.grindset.backend.dto;

import java.util.List;

/**
 * 一次答题后的结果:一句话点评 + 追问或本题评分。
 * nextAction = ANSWER_FOLLOW_UP / NEXT_QUESTION / REPORT_READY;
 * wrongQuestionId 为本题被收录为错题时的 ID,未收录为 null。
 */
public record AnswerResult(
        String feedback,
        String followUpQuestion,
        boolean questionFinished,
        Evaluation evaluation,
        String nextAction,
        int completedQuestions,
        int questionCount,
        String wrongQuestionId) {

    public record Evaluation(
            int score,
            List<String> knowledgePoints,
            String comment,
            String suggestion) {
    }
}
