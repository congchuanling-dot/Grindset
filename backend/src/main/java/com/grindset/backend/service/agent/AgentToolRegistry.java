package com.grindset.backend.service.agent;

import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.dto.KnowledgeProfileView;
import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.dto.WrongQuestionView;
import com.grindset.backend.service.InterviewService;
import com.grindset.backend.service.KnowledgeProfileService;
import com.grindset.backend.service.QuestionBankService;
import com.grindset.backend.service.WrongQuestionService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 工具注册表。第一版只放只读查询,复用现有各业务 Service,不新增表也不改接口。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    private final KnowledgeProfileService knowledgeProfileService;
    private final WrongQuestionService wrongQuestionService;
    private final QuestionBankService questionBankService;
    private final InterviewService interviewService;

    public AgentToolRegistry(KnowledgeProfileService knowledgeProfileService,
                             WrongQuestionService wrongQuestionService,
                             QuestionBankService questionBankService,
                             InterviewService interviewService) {
        this.knowledgeProfileService = knowledgeProfileService;
        this.wrongQuestionService = wrongQuestionService;
        this.questionBankService = questionBankService;
        this.interviewService = interviewService;

        register(knowledgeProfileTool());
        register(wrongQuestionsTool());
        register(questionStatsTool());
        register(listQuestionsTool());
        register(recentInterviewsTool());
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    /** 找不到返回 null,由调用方决定怎么提示模型 */
    public AgentTool find(String name) {
        return name == null ? null : tools.get(name);
    }

    private void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    // ---------- 工具实现 ----------

    private AgentTool knowledgeProfileTool() {
        return new SimpleTool("get_knowledge_profile", "查询掌握度画像",
                "查询当前用户的掌握度画像。可选参数:level(WEAK/LEARNING/MASTERED)、"
                        + "domain(知识领域)、limit(返回条数,默认10,最多20)。"
                        + "返回知识点名称、领域、掌握度(0-100)、等级、练习次数、最近练习时间。",
                (userId, args) -> {
                    KnowledgeProfileView view = knowledgeProfileService.profile(userId,
                            normalizeLevel(stringArg(args, "level")),
                            stringArg(args, "domain"),
                            intArg(args, "limit", 10, 1, 20));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("summary", view.summary());
                    data.put("items", view.items());
                    KnowledgeProfileView.Summary s = view.summary();
                    String summary = String.format(
                            "共 %d 个知识点(薄弱 %d / 学习中 %d / 已掌握 %d),平均掌握度 %d,本次返回 %d 条",
                            s.total(), s.weakCount(), s.learningCount(), s.masteredCount(),
                            s.averageMastery(), view.items() == null ? 0 : view.items().size());
                    return new AgentToolResult(true, summary, data);
                });
    }

    private AgentTool wrongQuestionsTool() {
        return new SimpleTool("get_wrong_questions", "查询错题本",
                "查询当前用户的错题本。可选参数:status(PENDING 待复习 / MASTERED 已掌握)、"
                        + "kp(知识点名称)、limit(默认10,最多20)。返回错题统计,以及题干摘要、知识点、原题得分。",
                (userId, args) -> {
                    WrongQuestionView view = wrongQuestionService.list(userId,
                            normalizeWrongStatus(stringArg(args, "status")),
                            null,
                            stringArg(args, "kp"),
                            null,
                            intArg(args, "limit", 10, 1, 20));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("summary", view.summary());
                    data.put("items", view.items());
                    WrongQuestionView.Summary s = view.summary();
                    String summary = String.format("错题共 %d 条,待复习 %d 条,已掌握 %d 条,已到复习时间 %d 条",
                            s.total(), s.pendingCount(), s.masteredCount(), s.dueCount());
                    return new AgentToolResult(true, summary, data);
                });
    }

    private AgentTool questionStatsTool() {
        return new SimpleTool("get_question_stats", "查询刷题统计",
                "查询当前用户的题库做题统计:总题数、已做、错题、待做、累计作答次数。无参数。",
                (userId, args) -> {
                    QuestionBankView.Summary s = questionBankService.stats(userId);
                    String summary = String.format("题库共 %d 题:已做 %d,错题 %d,待做 %d,累计作答 %d 次",
                            s.total(), s.done(), s.wrong(), s.todo(), s.totalAttempts());
                    return new AgentToolResult(true, summary, s);
                });
    }

    private AgentTool listQuestionsTool() {
        return new SimpleTool("list_questions", "查询题库题目",
                "按条件查询题库题目。可选参数:domain(领域)、difficulty(1简单/2中等/3困难)、"
                        + "status(TODO 未做 / DONE 已做 / WRONG 错题)、limit(默认10,最多20)。"
                        + "返回题目 ID、标题、领域、难度、我的做题状态、知识点。",
                (userId, args) -> {
                    QuestionBankView view = questionBankService.list(userId,
                            stringArg(args, "domain"),
                            nullableIntArg(args, "difficulty", 1, 3),
                            normalizeQuestionStatus(stringArg(args, "status")),
                            null, 1, intArg(args, "limit", 10, 1, 20));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("summary", view.summary());
                    data.put("items", view.items());
                    String summary = String.format("命中 %d 题,本次返回 %d 题",
                            view.filteredTotal(), view.items() == null ? 0 : view.items().size());
                    return new AgentToolResult(true, summary, data);
                });
    }

    private AgentTool recentInterviewsTool() {
        return new SimpleTool("list_recent_interviews", "查询面试记录",
                "查询最近的 AI 模拟面试记录。可选参数:limit(默认5,最多20)。"
                        + "返回岗位、轮次、状态(0进行中/1已结束)、总分、完成题数/总题数、创建时间。",
                (userId, args) -> {
                    int limit = intArg(args, "limit", 5, 1, 20);
                    List<InterviewSummary> all = interviewService.list(userId, null);
                    List<InterviewSummary> items = all.size() > limit ? all.subList(0, limit) : all;
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("total", all.size());
                    data.put("items", items);
                    String summary = all.isEmpty()
                            ? "还没有面试记录"
                            : String.format("共 %d 场面试,本次返回最近 %d 场", all.size(), items.size());
                    return new AgentToolResult(true, summary, data);
                });
    }

    // ---------- 参数解析 ----------

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue, int min, int max) {
        Integer value = nullableIntArg(args, key, min, max);
        return value == null ? defaultValue : value;
    }

    /** 模型可能把数字写成字符串,这里都兼容;超范围直接夹到边界 */
    private static Integer nullableIntArg(Map<String, Object> args, String key, int min, int max) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static String normalizeLevel(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "WEAK", "LEARNING", "MASTERED" -> text;
            default -> null;
        };
    }

    private static String normalizeWrongStatus(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "PENDING", "MASTERED" -> text;
            default -> null;
        };
    }

    private static String normalizeQuestionStatus(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "TODO", "DONE", "WRONG" -> text;
            default -> null;
        };
    }

    // ---------- 内部类型 ----------

    @FunctionalInterface
    private interface Handler {
        AgentToolResult run(long userId, Map<String, Object> args);
    }

    private record SimpleTool(String name, String label, String description, Handler handler)
            implements AgentTool {

        @Override
        public AgentToolResult execute(long userId, Map<String, Object> args) {
            return handler.run(userId, args);
        }
    }
}
