package com.grindset.backend.dto;

import java.util.List;

/**
 * 一次代码评审的历史记录。
 */
public record CodingReviewView(
        String id,
        String code,
        int score,
        List<String> hitPoints,
        List<String> missedPoints,
        String comment,
        String suggestion,
        String createdAt) {
}
