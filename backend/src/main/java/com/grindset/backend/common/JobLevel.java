package com.grindset.backend.common;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 面试岗位级别。数据库与前端使用小写 code,枚举名只用于代码内。
 */
public enum JobLevel {

    INTERN("intern", "实习"),
    CAMPUS("campus", "校招"),
    EXPERIENCED("experienced", "社招"),
    SENIOR("senior", "高级/专家");

    @EnumValue
    private final String code;

    private final String label;

    JobLevel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 按 code 或枚举名匹配,空值或不认识返回 null */
    public static JobLevel from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        for (JobLevel level : values()) {
            if (level.code.equalsIgnoreCase(value) || level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        return null;
    }

    /** 简历里识别出的候选人类型转岗位级别 */
    public static JobLevel fromCandidateType(String candidateType) {
        if (candidateType == null) {
            return null;
        }
        return switch (candidateType) {
            case "INTERN" -> INTERN;
            case "FRESH_GRADUATE" -> CAMPUS;
            case "EXPERIENCED" -> EXPERIENCED;
            default -> null;
        };
    }
}
