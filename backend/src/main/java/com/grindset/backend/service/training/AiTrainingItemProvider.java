package com.grindset.backend.service.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.GeneratedQuestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 题源：按难度分组，一次调用生成一组缺失题目，题目内容快照进 generated_json。
 */
@Component
public class AiTrainingItemProvider implements TrainingItemProvider {

    private final TrainingLlmService llmService;
    private final ObjectMapper objectMapper;

    public AiTrainingItemProvider(TrainingLlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TrainingItemDraft> provide(TrainingGenerationContext context,
                                           List<TrainingTargetSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<TrainingTargetSlot>> byDifficulty = new LinkedHashMap<>();
        for (TrainingTargetSlot slot : slots) {
            if (slot.count() <= 0) {
                continue;
            }
            int difficulty = slot.difficulty();
            byDifficulty.computeIfAbsent(difficulty, key -> new ArrayList<>()).add(slot);
        }

        List<TrainingItemDraft> result = new ArrayList<>();
        for (Map.Entry<Integer, List<TrainingTargetSlot>> entry : byDifficulty.entrySet()) {
            int difficulty = entry.getKey();
            List<TrainingTargetSlot> group = entry.getValue();
            List<TrainingTarget> targets = new ArrayList<>();
            int total = 0;
            for (TrainingTargetSlot slot : group) {
                if (!containsTarget(targets, slot.target().kpKey())) {
                    targets.add(slot.target());
                }
                total += slot.count();
            }
            if (total <= 0) {
                continue;
            }
            List<GeneratedQuestion> questions = llmService.generateQuestions(
                    context, context.stage(), targets, total, difficulty);
            Map<String, Integer> remaining = new LinkedHashMap<>();
            for (TrainingTargetSlot slot : group) {
                remaining.merge(slot.target().kpKey(), slot.count(), Integer::sum);
            }
            for (GeneratedQuestion question : questions) {
                String kpKey = KnowledgePointKeys.keyOf(question.kpName());
                Integer left = remaining.get(kpKey);
                if (left == null || left <= 0) {
                    continue;
                }
                TrainingTargetSlot slot = slotOf(group, kpKey);
                result.add(new TrainingItemDraft(
                        kpKey,
                        question.kpName(),
                        slot == null ? null : slot.target().dimension(),
                        slot == null ? "PRACTICE" : slot.role(),
                        "TEXT",
                        question.difficulty() == null ? difficulty : question.difficulty(),
                        null,
                        toJson(question),
                        question.title()));
                remaining.put(kpKey, left - 1);
            }
        }
        return result;
    }

    private static boolean containsTarget(List<TrainingTarget> targets, String kpKey) {
        for (TrainingTarget target : targets) {
            if (target.kpKey().equals(kpKey)) {
                return true;
            }
        }
        return false;
    }

    private static TrainingTargetSlot slotOf(List<TrainingTargetSlot> slots, String kpKey) {
        for (TrainingTargetSlot slot : slots) {
            if (slot.target().kpKey().equals(kpKey)) {
                return slot;
            }
        }
        return null;
    }

    private String toJson(GeneratedQuestion question) {
        try {
            return objectMapper.writeValueAsString(question);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "训练题序列化失败");
        }
    }
}
