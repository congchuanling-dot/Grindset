package com.grindset.backend.service.agent.tool;

import java.util.Map;

/**
 * 工具参数的容错解析。模型经常省略可选参数,也常把数字写成字符串。
 */
public final class AgentToolArgs {

    private AgentToolArgs() {
    }

    public static String string(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    public static int integer(Map<String, Object> args, String key, int defaultValue, int min, int max) {
        Integer value = nullableInteger(args, key, min, max);
        return value == null ? defaultValue : value;
    }

    /** 超范围直接夹到边界,避免模型传 999 把数据全捞出来 */
    public static Integer nullableInteger(Map<String, Object> args, String key, int min, int max) {
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
}
