package com.grindset.backend.service.training;

import com.grindset.backend.common.TrainingStage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练目标选择：把“这个阶段该练谁”从会话生命周期里拆出来。
 */
@Component
public class TrainingTargetSelector {

    private final TrainingTargetFactory targetFactory;
    private final WeaknessDiagnosisService weaknessService;
    private final MasteryService masteryService;
    private final MistakeService mistakeService;

    public TrainingTargetSelector(TrainingTargetFactory targetFactory,
                                  WeaknessDiagnosisService weaknessService,
                                  MasteryService masteryService,
                                  MistakeService mistakeService) {
        this.targetFactory = targetFactory;
        this.weaknessService = weaknessService;
        this.masteryService = masteryService;
        this.mistakeService = mistakeService;
    }

    public List<TrainingTarget> select(TrainingStage stage, long userId, Long sessionId) {
        return switch (stage) {
            case BASELINE -> targetFactory.baseline(userId);
            case RETEST -> retest(userId, sessionId);
            case MISTAKE -> targetFactory.fromMistakes(userId, mistakeService.open(userId, 5));
            case DAILY -> daily(userId);
        };
    }

    private List<TrainingTarget> retest(long userId, Long sessionId) {
        List<TrainingTarget> result = sessionId == null ? List.of()
                : targetFactory.fromWeaknesses(userId,
                weaknessService.listBySession(userId, sessionId));
        return result.isEmpty()
                ? targetFactory.fromMastery(userId, masteryService.weakItems(userId, 3))
                : result;
    }

    private List<TrainingTarget> daily(long userId) {
        Map<String, TrainingTarget> byKey = new LinkedHashMap<>();
        for (TrainingTarget target : targetFactory.fromWeaknesses(
                userId, weaknessService.listLatest(userId, 8))) {
            byKey.putIfAbsent(target.kpKey(), target);
        }
        for (TrainingTarget target : targetFactory.fromMastery(
                userId, masteryService.weakItems(userId, 8))) {
            byKey.putIfAbsent(target.kpKey(), target);
        }
        List<TrainingTarget> result = new ArrayList<>(byKey.values());
        return result.isEmpty() ? targetFactory.baseline(userId) : result;
    }
}
