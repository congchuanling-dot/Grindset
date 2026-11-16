package com.grindset.backend.dto;

/** 提交一次错题重练。 */
public class RetryRequest {

    private String answer;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
