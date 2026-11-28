package com.grindset.backend.common;

/**
 * 训练阶段。基线建立画像，日常补弱，复测验证，错题重练清理历史问题。
 */
public enum TrainingStage {

    BASELINE,
    DAILY,
    RETEST,
    MISTAKE;

    public static TrainingStage from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DAILY;
        }
    }
}
