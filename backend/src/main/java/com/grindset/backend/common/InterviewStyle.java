package com.grindset.backend.common;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 面试官风格。数据库存小写 code,前端也使用小写,枚举名只用于代码内。
 */
public enum InterviewStyle {

    PRESSURE("pressure"),
    MIDDLE("middle"),
    RELAXED("relaxed");

    @EnumValue
    private final String code;

    InterviewStyle(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 按 code 匹配,空值或不认识返回 null */
    public static InterviewStyle from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        for (InterviewStyle style : values()) {
            if (style.code.equalsIgnoreCase(value) || style.name().equalsIgnoreCase(value)) {
                return style;
            }
        }
        return null;
    }
}
