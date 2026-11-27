package com.grindset.backend.service.interview;

import com.grindset.backend.dto.JdProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把口述题数按权重分配到 JD 能力项,保证 3/5/8 题都能覆盖画像。
 * 纯计算,不调用模型,同一场面试每次得到的结果稳定。
 */
public final class InterviewJdPlan {

    private InterviewJdPlan() {
    }

    /** 第 seq 题(1-based)考察的能力项;未启用 JD 或没有能力项时返回 null */
    public static String competencyFor(JdProfile profile, int seq, int verbalCount) {
        List<JdProfile.Competency> competencies = valid(profile);
        if (competencies.isEmpty() || seq < 1 || verbalCount < 1) {
            return null;
        }
        List<JdProfile.Competency> sequence = expand(competencies, verbalCount);
        if (sequence.isEmpty()) {
            return null;
        }
        return sequence.get(Math.min(seq - 1, sequence.size() - 1)).name();
    }

    /** 能力项对应的 JD 期望,用于出题和评分提示 */
    public static String expectationOf(JdProfile profile, String competency) {
        if (profile == null || profile.competencies() == null || competency == null) {
            return null;
        }
        for (JdProfile.Competency item : profile.competencies()) {
            if (item != null && competency.equals(item.name())) {
                return item.expectation();
            }
        }
        return null;
    }

    private static List<JdProfile.Competency> valid(JdProfile profile) {
        if (profile == null || profile.competencies() == null) {
            return List.of();
        }
        List<JdProfile.Competency> result = new ArrayList<>();
        for (JdProfile.Competency item : profile.competencies()) {
            if (item != null && item.name() != null && !item.name().isBlank()) {
                result.add(item);
            }
        }
        return result;
    }

    /** 先按最大余数分配题数,再按权重顺序轮询展开,避免连续两题同一能力项 */
    private static List<JdProfile.Competency> expand(List<JdProfile.Competency> competencies,
                                                     int total) {
        List<JdProfile.Competency> sorted = new ArrayList<>(competencies);
        sorted.sort(Comparator.comparingInt(InterviewJdPlan::weightOf).reversed());

        int[] weights = sorted.stream().mapToInt(InterviewJdPlan::weightOf).toArray();
        int sum = 0;
        for (int weight : weights) {
            sum += weight;
        }

        int[] counts = new int[sorted.size()];
        int assigned = 0;
        double[] remainders = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            double share = sum <= 0
                    ? (double) total / sorted.size()
                    : (double) weights[i] / sum * total;
            counts[i] = (int) Math.floor(share);
            remainders[i] = share - counts[i];
            assigned += counts[i];
        }

        Integer[] order = new Integer[sorted.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int byRemainder = Double.compare(remainders[b], remainders[a]);
            return byRemainder != 0 ? byRemainder : Integer.compare(weights[b], weights[a]);
        });

        int left = Math.max(0, total - assigned);
        for (int i = 0; i < left; i++) {
            counts[order[i % order.length]]++;
        }

        List<JdProfile.Competency> result = new ArrayList<>(total);
        int[] remaining = counts.clone();
        int cursor = 0;
        while (result.size() < total) {
            boolean added = false;
            for (int i = 0; i < sorted.size(); i++) {
                int index = order[i];
                if (remaining[index] > 0) {
                    result.add(sorted.get(index));
                    remaining[index]--;
                    added = true;
                    if (result.size() >= total) {
                        break;
                    }
                }
            }
            if (!added) {
                // 权重极端时兜底,用权重最高的能力项补满
                result.add(sorted.get(cursor % sorted.size()));
                cursor++;
            }
        }
        return result;
    }

    private static int weightOf(JdProfile.Competency competency) {
        return competency.weight() == null ? 0 : Math.max(0, competency.weight());
    }
}
