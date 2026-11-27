package com.grindset.backend.common;

/**
 * AI 用量业务类型。数据库存 {@code name()},前端展示 {@link #label()}。
 * 新增业务场景时只加枚举,不改表结构。
 */
public enum AiBizType {

    HOME_CHAT("AI 对话"),
    AGENT_CHAT("AI 助手"),
    AGENT_TITLE("会话标题"),
    INTERVIEW_QUESTION("面试出题"),
    INTERVIEW_DECISION("面试点评"),
    INTERVIEW_EVALUATION("面试评分"),
    INTERVIEW_JD_PARSE("JD 解析"),
    INTERVIEW_CODING_CHAT("面试编程交流"),
    INTERVIEW_CODING_FEEDBACK("面试编程点评"),
    INTERVIEW_REPORT("面试复盘"),
    BANK_EVALUATION("题库评分"),
    BANK_CODING_REVIEW("题库代码点评"),
    RESUME_PARSE("简历解析");

    private final String label;

    AiBizType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 库里出现未知类型时按原名展示,不让历史数据把接口打挂 */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) {
            return "其他";
        }
        for (AiBizType type : values()) {
            if (type.name().equals(name)) {
                return type.label;
            }
        }
        return name;
    }
}
