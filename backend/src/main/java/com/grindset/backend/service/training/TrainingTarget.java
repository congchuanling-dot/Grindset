package com.grindset.backend.service.training;

/** 一个待补强的目标知识点，携带生成时的掌握度、维度与调度标记。 */
public record TrainingTarget(
        String kpKey,
        String kpName,
        String dimension,
        int severity,
        int beforeMastery,
        String evidence,
        String advice,
        boolean due,
        boolean stretch) {
}
