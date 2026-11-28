package com.grindset.backend.service.training;

import com.grindset.backend.common.TrainingStage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.grindset.backend.common.constant.TrainingConstants.ROLE_PRACTICE;
import static com.grindset.backend.common.constant.TrainingConstants.ROLE_RETEST;

/**
 * 自适应训练策略：决定每个阶段练哪些知识点、几道题、什么难度、什么角色。
 *
 * <p>策略只做纯计算，不碰数据库与 LLM，方便单测和后续替换成更复杂的 CAT/IRT。</p>
 */
@Component
public class AdaptiveTrainingPolicy {

    public Blueprint plan(TrainingStage stage, List<TrainingTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return new Blueprint(List.of(), null);
        }
        List<TrainingTarget> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingInt(TrainingTarget::severity).reversed()
                .thenComparingInt(TrainingTarget::beforeMastery));
        return switch (stage) {
            case BASELINE -> baseline(sorted);
            case RETEST -> retest(sorted);
            case MISTAKE -> mistake(sorted);
            case DAILY -> daily(sorted);
        };
    }

    /** 下一题难度：最近两题都高分则升，最近一题低分则降。 */
    public int nextDifficulty(int currentDifficulty, List<Integer> recentScores) {
        int base = Math.max(1, Math.min(3, currentDifficulty));
        if (recentScores == null || recentScores.isEmpty()) {
            return base;
        }
        int last = recentScores.get(recentScores.size() - 1);
        if (last <= 2) {
            return Math.max(1, base - 1);
        }
        if (recentScores.size() >= 2) {
            int previous = recentScores.get(recentScores.size() - 2);
            if (previous >= 4 && last >= 4) {
                return Math.min(3, base + 1);
            }
        }
        return base;
    }

    public int difficultyForMastery(int mastery) {
        if (mastery < 40) {
            return 1;
        }
        return mastery < 75 ? 2 : 3;
    }

    private Blueprint baseline(List<TrainingTarget> targets) {
        Set<String> dimensions = new LinkedHashSet<>();
        List<TrainingTargetSlot> slots = new ArrayList<>();
        for (TrainingTarget target : targets) {
            if (dimensions.add(target.dimension()) && slots.size() < 8) {
                slots.add(new TrainingTargetSlot(target, 1, 2, ROLE_PRACTICE));
            }
        }
        return new Blueprint(slots, dominantDimension(slots));
    }

    private Blueprint daily(List<TrainingTarget> targets) {
        List<TrainingTargetSlot> slots = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();

        // 1~2 最弱知识点
        int weakCount = 0;
        for (TrainingTarget target : targets) {
            if (weakCount >= 2) {
                break;
            }
            if (target.beforeMastery() < 40) {
                slots.add(slot(target, difficultyForMastery(target.beforeMastery()), ROLE_PRACTICE));
                used.add(target.kpKey());
                weakCount++;
            }
        }
        while (slots.size() < 2) {
            TrainingTarget next = null;
            for (TrainingTarget target : targets) {
                if (used.add(target.kpKey())) {
                    next = target;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            slots.add(slot(next, difficultyForMastery(next.beforeMastery()), ROLE_PRACTICE));
        }

        // 3 到期复习
        addFirst(slots, used, targets, target -> target.due());
        // 4 拉伸题
        addFirst(slots, used, targets, target -> target.stretch(), 1);
        // 5 复测
        TrainingTarget retest = targets.stream()
                .min(Comparator.comparingInt(TrainingTarget::beforeMastery))
                .orElse(targets.get(0));
        slots.add(new TrainingTargetSlot(retest, 1,
                difficultyForMastery(retest.beforeMastery()), ROLE_RETEST));

        // 不足 5 题时继续补未使用的知识点
        for (TrainingTarget target : targets) {
            if (slots.size() >= 5) {
                break;
            }
            if (used.add(target.kpKey())) {
                slots.add(slot(target, difficultyForMastery(target.beforeMastery()), ROLE_PRACTICE));
            }
        }
        return new Blueprint(slots, dominantDimension(slots));
    }

    private Blueprint retest(List<TrainingTarget> targets) {
        List<TrainingTargetSlot> slots = new ArrayList<>();
        for (int i = 0; i < targets.size() && i < 3; i++) {
            TrainingTarget target = targets.get(i);
            slots.add(new TrainingTargetSlot(target, 1,
                    difficultyForMastery(target.beforeMastery()), ROLE_RETEST));
        }
        return new Blueprint(slots, dominantDimension(slots));
    }

    private Blueprint mistake(List<TrainingTarget> targets) {
        List<TrainingTargetSlot> slots = new ArrayList<>();
        for (int i = 0; i < targets.size() && i < 5; i++) {
            TrainingTarget target = targets.get(i);
            slots.add(new TrainingTargetSlot(target, 1,
                    difficultyForMastery(target.beforeMastery()), ROLE_PRACTICE));
        }
        if (!slots.isEmpty()) {
            TrainingTargetSlot last = slots.remove(slots.size() - 1);
            slots.add(new TrainingTargetSlot(last.target(), 1, last.difficulty(), ROLE_RETEST));
        }
        return new Blueprint(slots, dominantDimension(slots));
    }

    private void addFirst(List<TrainingTargetSlot> slots, Set<String> used,
                          List<TrainingTarget> targets,
                          java.util.function.Predicate<TrainingTarget> predicate) {
        addFirst(slots, used, targets, predicate, 0);
    }

    private void addFirst(List<TrainingTargetSlot> slots, Set<String> used,
                          List<TrainingTarget> targets,
                          java.util.function.Predicate<TrainingTarget> predicate,
                          int difficultyBonus) {
        for (TrainingTarget target : targets) {
            if (used.contains(target.kpKey()) || !predicate.test(target)) {
                continue;
            }
            int difficulty = Math.min(3, difficultyForMastery(target.beforeMastery()) + difficultyBonus);
            slots.add(new TrainingTargetSlot(target, 1, difficulty, ROLE_PRACTICE));
            used.add(target.kpKey());
            return;
        }
    }

    private TrainingTargetSlot slot(TrainingTarget target, int difficulty, String role) {
        return new TrainingTargetSlot(target, 1, difficulty, role);
    }

    private String dominantDimension(List<TrainingTargetSlot> slots) {
        if (slots.isEmpty()) {
            return null;
        }
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (TrainingTargetSlot slot : slots) {
            counts.merge(slot.target().dimension(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    public record Blueprint(List<TrainingTargetSlot> slots, String dimensionFocus) {
    }
}
