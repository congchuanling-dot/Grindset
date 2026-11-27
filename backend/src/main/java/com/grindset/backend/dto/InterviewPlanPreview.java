package com.grindset.backend.dto;

/** 面试计划预览结果:解析出的岗位画像 + 推荐计划 + 最终岗位名称 */
public record InterviewPlanPreview(
        String position,
        JdProfile jdProfile,
        InterviewPlan plan) {
}
