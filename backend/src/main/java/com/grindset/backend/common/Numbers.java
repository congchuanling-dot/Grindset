package com.grindset.backend.common;

/**
 * 数值兜底转换。各 Service 里重复出现的 null 转 0 统一收在这里。
 */
public final class Numbers {

    private Numbers() {
    }

    public static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
