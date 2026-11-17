package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.service.QuestionBankService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 题库做题统计,无参数。
 */
@Component
public class QuestionBankStatsTool implements AgentTool {

    private final QuestionBankService questionBankService;

    public QuestionBankStatsTool(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @Override
    public String name() {
        return "get_question_stats";
    }

    @Override
    public String label() {
        return "查询刷题统计";
    }

    @Override
    public String description() {
        return "查询当前用户的题库做题统计:总题数、已做、错题、待做、累计作答次数。无参数。";
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        QuestionBankView.Summary s = questionBankService.stats(userId);
        String summary = String.format("题库共 %d 题:已做 %d,错题 %d,待做 %d,累计作答 %d 次",
                s.total(), s.done(), s.wrong(), s.todo(), s.totalAttempts());
        return new AgentToolResult(true, summary, s);
    }
}
