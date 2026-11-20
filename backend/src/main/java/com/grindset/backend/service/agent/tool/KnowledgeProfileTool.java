package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.KnowledgeProfileView;
import com.grindset.backend.service.KnowledgeProfileService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 掌握度画像查询。支持按等级、领域过滤,默认取需求最高的 10 条。
 */
@Component
public class KnowledgeProfileTool implements AgentTool {

    private final KnowledgeProfileService knowledgeProfileService;

    public KnowledgeProfileTool(KnowledgeProfileService knowledgeProfileService) {
        this.knowledgeProfileService = knowledgeProfileService;
    }

    @Override
    public String name() {
        return "get_knowledge_profile";
    }

    @Override
    public String label() {
        return "查询掌握度画像";
    }

    @Override
    public String description() {
        return "查询当前用户的掌握度画像。可选参数:level(WEAK/LEARNING/MASTERED)、"
                + "domain(知识领域)、limit(返回条数,默认10,最多20)。"
                + "返回知识点名称、领域、掌握度(0-100)、等级、练习次数、最近练习时间。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "level", Map.of("type", "string",
                                "enum", List.of("WEAK", "LEARNING", "MASTERED"),
                                "description", "掌握度等级,不传则返回全部"),
                        "domain", Map.of("type", "string",
                                "description", "知识领域,如 Java、MySQL、算法"),
                        "limit", Map.of("type", "integer",
                                "description", "返回条数,默认 10,最多 20")),
                "required", List.of());
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        KnowledgeProfileView view = knowledgeProfileService.profile(userId,
                normalizeLevel(AgentToolArgs.string(args, "level")),
                AgentToolArgs.string(args, "domain"),
                AgentToolArgs.integer(args, "limit", 10, 1, 20));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", view.summary());
        data.put("items", view.items());

        KnowledgeProfileView.Summary s = view.summary();
        String summary = String.format(
                "共 %d 个知识点(薄弱 %d / 学习中 %d / 已掌握 %d),平均掌握度 %d,本次返回 %d 条",
                s.total(), s.weakCount(), s.learningCount(), s.masteredCount(),
                s.averageMastery(), view.items() == null ? 0 : view.items().size());
        return new AgentToolResult(true, summary, data);
    }

    private static String normalizeLevel(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "WEAK", "LEARNING", "MASTERED" -> text;
            default -> null;
        };
    }
}
