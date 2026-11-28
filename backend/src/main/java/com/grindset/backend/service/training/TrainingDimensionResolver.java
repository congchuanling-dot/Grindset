package com.grindset.backend.service.training;

import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.QuestionBank;

/**
 * 维度映射的唯一入口：题目/面试题 -> 五维能力。
 */
public final class TrainingDimensionResolver {

    private TrainingDimensionResolver() {
    }

    public static TrainingDimension of(QuestionBank question) {
        if (question == null) {
            return TrainingDimension.BASICS;
        }
        return TrainingDimension.of(question.getType(), question.getJudgeMode(), question.getDomain());
    }

    public static TrainingDimension of(InterviewQuestion question) {
        if (question == null) {
            return TrainingDimension.BASICS;
        }
        if (question.getQuestionType() != null
                && "CODING".equals(question.getQuestionType().name())) {
            return TrainingDimension.CODING;
        }
        String competency = question.getCompetency();
        if (competency != null) {
            if (competency.contains("表达") || competency.contains("沟通")) {
                return TrainingDimension.COMMUNICATION;
            }
            if (competency.contains("系统设计") || competency.contains("架构")) {
                return TrainingDimension.SYSTEM_DESIGN;
            }
            if (competency.contains("项目")) {
                return TrainingDimension.PROJECT;
            }
        }
        return TrainingDimension.BASICS;
    }
}
