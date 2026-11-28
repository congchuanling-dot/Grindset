package com.grindset.backend.common.constant;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一的时间展示格式。
 *
 * <p>项目所有对外时间都按 {@link #STANDARD} 输出,避免各接口出现不同格式。
 * {@link DateTimeFormatter} 是线程安全的,可以安全复用同一个实例。</p>
 */
public final class TimeFormats {

    /** 对外展示格式:2026-11-17 15:20 */
    public static final DateTimeFormatter STANDARD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TimeFormats() {
    }

    /** 空值原样返回 null,其余按 {@link #STANDARD} 格式化 */
    public static String format(LocalDateTime time) {
        return time == null ? null : STANDARD.format(time);
    }
}
