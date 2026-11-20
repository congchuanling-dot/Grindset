package com.grindset.backend.common;

/**
 * 零散的文本处理。各业务里重复出现的 strip / 空串转 null / 截断统一收在这里。
 */
public final class Texts {

    private Texts() {
    }

    public static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
