package com.grindset.backend.common;

/**
 * 候选人阶段。简历里的"年限"口径不统一(应届/实习/社招各算各的),
 * 因此只记录阶段,具体经历看章节数据。
 */
public enum CandidateType {

    /** 应届校招 */
    FRESH_GRADUATE,
    /** 找实习 */
    INTERN,
    /** 社招 */
    EXPERIENCED,
    /** 判断不了 */
    UNKNOWN;

    public static CandidateType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
