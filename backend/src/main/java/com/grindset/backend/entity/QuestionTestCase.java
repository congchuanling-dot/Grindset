package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * question_test_cases 表:编程题测试用例。
 * is_sample=1 的用例会随题面下发,用于"运行样例";其余为隐藏用例。
 */
@TableName("question_test_cases")
public class QuestionTestCase {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long questionId;

    private String input;

    private String expectedOutput;

    /** 1 例题 / 0 隐藏用例 */
    private Integer isSample;

    private Integer sort;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public Integer getIsSample() {
        return isSample;
    }

    public void setIsSample(Integer isSample) {
        this.isSample = isSample;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
