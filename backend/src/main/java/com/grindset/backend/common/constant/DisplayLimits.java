package com.grindset.backend.common.constant;

/**
 * 列表类响应里文本截断长度。错题本和题库的标题/题干摘要共用同一口径,
 * 保证两个页面的列表观感一致。
 */
public final class DisplayLimits {

    /** 题干、回答等长文本在列表中的摘要长度(字符) */
    public static final int MAX_PREVIEW_CHARS = 120;

    private DisplayLimits() {
    }
}
