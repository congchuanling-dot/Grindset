package com.grindset.backend.service.training;

import com.grindset.backend.common.JudgeMode;
import com.grindset.backend.entity.QuestionBank;

/**
 * 题库题与知识点的匹配规则，训练选题、同类题、下一题替换共用。
 */
public final class QuestionKpMatcher {

    private QuestionKpMatcher() {
    }

    public static boolean isText(QuestionBank question) {
        return question != null
                && (question.getJudgeMode() == null || question.getJudgeMode() == JudgeMode.TEXT);
    }

    public static boolean contains(QuestionBank question, String kpKey) {
        if (question == null || kpKey == null || kpKey.isBlank() || question.getKpNames() == null) {
            return false;
        }
        for (String name : question.getKpNames()) {
            if (kpKey.equals(KnowledgePointKeys.keyOf(name))) {
                return true;
            }
        }
        return false;
    }
}
