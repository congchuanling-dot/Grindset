package com.grindset.backend.dto;

import java.util.List;

/** 训练仪表盘：五维能力、成长曲线、弱项地图、到期与错题。 */
public record TrainingDashboard(
        int readiness,
        int readinessDelta7d,
        boolean hasBaseline,
        List<DimensionView> dimensions,
        List<GrowthPoint> growth,
        List<WeaknessItem> weaknesses,
        int dueCount,
        int mistakeCount,
        ActivePlan activePlan,
        List<TrainingMistakeView> recentMistakes) {

    public record DimensionView(String code, String label, int mastery, int delta7d) {
    }

    public record GrowthPoint(String date, int mastery) {
    }

    public record WeaknessItem(
            String kpKey,
            String kpName,
            String dimension,
            String dimensionLabel,
            int mastery,
            String level,
            String evidence,
            String nextReviewAt,
            boolean eliminated) {
    }

    public record ActivePlan(String id, String title, String stage,
                             int completedCount, int itemCount) {
    }
}
