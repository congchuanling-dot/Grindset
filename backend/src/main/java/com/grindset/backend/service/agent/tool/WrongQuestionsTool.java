package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.WrongQuestionView;
import com.grindset.backend.service.WrongQuestionService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 错题本查询。支持按复习状态和知识点过滤。
 */
@Component
public class WrongQuestionsTool implements AgentTool {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionsTool(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @Override
    public String name() {
        return "get_wrong_questions";
    }

    @Override
    public String label() {
        return "查询错题本";
    }

    @Override
    public String description() {
        return "查询当前用户的错题本。可选参数:status(PENDING 待复习 / MASTERED 已掌握)、"
                + "kp(知识点名称)、limit(默认10,最多20)。返回错题统计,以及题干摘要、知识点、原题得分。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string",
                                "enum", List.of("PENDING", "MASTERED"),
                                "description", "复习状态"),
                        "kp", Map.of("type", "string", "description", "知识点名称"),
                        "limit", Map.of("type", "integer",
                                "description", "返回条数,默认 10,最多 20")),
                "required", List.of());
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        WrongQuestionView view = wrongQuestionService.list(userId,
                normalizeStatus(AgentToolArgs.string(args, "status")),
                null,
                AgentToolArgs.string(args, "kp"),
                null,
                AgentToolArgs.integer(args, "limit", 10, 1, 20));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", view.summary());
        data.put("items", view.items());

        WrongQuestionView.Summary s = view.summary();
        String summary = String.format("错题共 %d 条,待复习 %d 条,已掌握 %d 条,已到复习时间 %d 条",
                s.total(), s.pendingCount(), s.masteredCount(), s.dueCount());
        return new AgentToolResult(true, summary, data);
    }

    private static String normalizeStatus(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "PENDING", "MASTERED" -> text;
            default -> null;
        };
    }
}
