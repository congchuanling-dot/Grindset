package com.grindset.backend.service.training;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 知识点名称归一化。训练选题、掌握度聚合、弱项去重都以归一化键为准。
 */
public final class KnowledgePointKeys {

    private static final Pattern NOISE = Pattern.compile("[\\s\\-_/·（）()【】\\[\\]：:，,。.、；;]");
    private static final int MAX_LENGTH = 80;

    private KnowledgePointKeys() {
    }

    /** 归一化键：去空白/标点、转小写、截断 */
    public static String keyOf(String name) {
        String display = displayName(name);
        if (display.isEmpty()) {
            return "";
        }
        String key = NOISE.matcher(display.toLowerCase(Locale.ROOT)).replaceAll("");
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH) : key;
    }

    /** 展示名：去首尾空格、压缩空白、截断 */
    public static String displayName(String name) {
        if (name == null) {
            return "";
        }
        String value = name.strip().replaceAll("\\s+", " ");
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
