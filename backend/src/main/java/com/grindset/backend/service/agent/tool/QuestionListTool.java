package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.service.QuestionBankService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按领域、难度、做题状态筛选题库题目。
 */
@Component
public class QuestionListTool implements AgentTool {

    private final QuestionBankService questionBankService;

    public QuestionListTool(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @Override
    public String name() {
        return "list_questions";
    }

    @Override
    public String label() {
        return "查询题库题目";
    }

    @Override
    public String description() {
        return "按条件查询题库题目。可选参数:domain(领域)、difficulty(1简单/2中等/3困难)、"
                + "status(TODO 未做 / DONE 已做 / WRONG 错题)、limit(默认10,最多20)。"
                + "返回题目 ID、标题、领域、难度、我的做题状态、知识点。";
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        QuestionBankView view = questionBankService.list(userId,
                AgentToolArgs.string(args, "domain"),
                AgentToolArgs.nullableInteger(args, "difficulty", 1, 3),
                normalizeStatus(AgentToolArgs.string(args, "status")),
                null, 1, AgentToolArgs.integer(args, "limit", 10, 1, 20));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", view.summary());
        data.put("items", view.items());

        String summary = String.format("命中 %d 题,本次返回 %d 题",
                view.filteredTotal(), view.items() == null ? 0 : view.items().size());
        return new AgentToolResult(true, summary, data);
    }

    private static String normalizeStatus(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "TODO", "DONE", "WRONG" -> text;
            default -> null;
        };
    }
}
