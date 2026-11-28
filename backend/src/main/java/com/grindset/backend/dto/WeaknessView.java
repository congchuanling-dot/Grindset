package com.grindset.backend.dto;

/**
 * 前端展示用的弱项视图。questionId 用字符串，避免 JS 精度丢失。
 */
public record WeaknessView(
        String kpKey,
        String kpName,
        String domain,
        String dimension,
        String dimensionLabel,
        int severity,
        String evidence,
        String advice,
        Integer questionSeq,
        String questionId) {
}
