package com.grindset.backend.dto;

import java.util.List;

/**
 * 从 JD 解析出的岗位画像。存 interview_sessions.jd_profile_json,
 * 出题、追问评分、复盘都按这份画像对齐。
 */
public record JdProfile(
        String title,
        String company,
        String level,
        List<String> mustHave,
        List<String> niceToHave,
        List<String> responsibilities,
        List<Competency> competencies,
        List<String> resumeGaps) {

    public record Competency(String name, Integer weight, String expectation) {
    }
}
