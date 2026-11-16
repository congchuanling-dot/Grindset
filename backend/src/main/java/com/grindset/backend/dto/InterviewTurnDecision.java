package com.grindset.backend.dto;

import java.util.List;

/**
 * 答题后由模型给出的结构化决策。
 * shouldFollowUp 为 true 时带 followUpQuestion;为 false 时必须带 evaluation。
 */
public class InterviewTurnDecision {

    private String feedback;

    private Boolean shouldFollowUp;

    private String followUpQuestion;

    private Evaluation evaluation;

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Boolean getShouldFollowUp() {
        return shouldFollowUp;
    }

    public void setShouldFollowUp(Boolean shouldFollowUp) {
        this.shouldFollowUp = shouldFollowUp;
    }

    public String getFollowUpQuestion() {
        return followUpQuestion;
    }

    public void setFollowUpQuestion(String followUpQuestion) {
        this.followUpQuestion = followUpQuestion;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    public static class Evaluation {

        private Integer score;

        private List<String> knowledgePoints;

        private String comment;

        private String suggestion;

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public List<String> getKnowledgePoints() {
            return knowledgePoints;
        }

        public void setKnowledgePoints(List<String> knowledgePoints) {
            this.knowledgePoints = knowledgePoints;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
}
