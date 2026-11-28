package com.grindset.backend.service.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.TrainingStage;
import com.grindset.backend.config.TrainingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.TrainingConstants.ROLE_PRACTICE;
import static com.grindset.backend.common.constant.TrainingConstants.ROLE_RETEST;

/**
 * 自适应会话编排：按策略给出的槽位，先题库、再 AI，最后补一道复测。
 * 具体题源由 {@link TrainingItemProvider} 扩展，编排层不关心题目从哪来。
 */
@Component
public class AdaptiveSessionBuilder {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveSessionBuilder.class);

    private final BankTrainingItemProvider bankProvider;
    private final AiTrainingItemProvider aiProvider;
    private final TrainingProperties properties;
    private final ObjectMapper objectMapper;

    public AdaptiveSessionBuilder(BankTrainingItemProvider bankProvider,
                                  AiTrainingItemProvider aiProvider,
                                  TrainingProperties properties,
                                  ObjectMapper objectMapper) {
        this.bankProvider = bankProvider;
        this.aiProvider = aiProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public GenerationResult build(TrainingGenerationContext context,
                                  TrainingStage stage,
                                  AdaptiveTrainingPolicy.Blueprint blueprint) {
        List<TrainingTargetSlot> slots = blueprint.slots();
        if (slots.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有可训练的知识点");
        }
        List<TrainingItemDraft> items = new ArrayList<>(bankProvider.provide(context, slots));

        List<TrainingTargetSlot> missing = missingSlots(slots, items);
        if (!missing.isEmpty()) {
            try {
                items.addAll(aiProvider.provide(context, limitSlots(missing, properties.getMaxAiItems())));
            } catch (BizException e) {
                log.warn("AI 训练题生成失败,降级题库: {}", e.getMessage());
            }
        }

        items.sort(Comparator.comparingInt(item -> ROLE_RETEST.equals(item.role()) ? 1 : 0));
        if (stage != TrainingStage.BASELINE
                && items.stream().noneMatch(item -> ROLE_RETEST.equals(item.role()))
                && !items.isEmpty()) {
            TrainingItemDraft last = items.remove(items.size() - 1);
            items.add(new TrainingItemDraft(last.kpKey(), last.kpName(), last.dimension(),
                    ROLE_RETEST, last.itemType(), last.difficulty(), last.questionId(),
                    last.generatedJson(), last.title()));
        }
        if (items.size() < minItems(stage)) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "暂时无法生成足够的练习题，请稍后重试");
        }
        String policyJson = toJson(slots.stream()
                .map(slot -> Map.of(
                        "kpKey", slot.target().kpKey(),
                        "dimension", slot.target().dimension(),
                        "difficulty", slot.difficulty(),
                        "role", slot.role(),
                        "count", slot.count()))
                .toList());
        return new GenerationResult(items, titleOf(stage, slots),
                blueprint.dimensionFocus(), policyJson);
    }

    private static List<TrainingTargetSlot> missingSlots(List<TrainingTargetSlot> slots,
                                                         List<TrainingItemDraft> picked) {
        Map<String, Integer> got = new LinkedHashMap<>();
        for (TrainingItemDraft item : picked) {
            got.merge(key(item.kpKey(), item.role()), 1, Integer::sum);
        }
        List<TrainingTargetSlot> missing = new ArrayList<>();
        for (TrainingTargetSlot slot : slots) {
            int count = got.getOrDefault(key(slot.target().kpKey(), slot.role()), 0);
            int left = slot.count() - count;
            if (left > 0) {
                missing.add(new TrainingTargetSlot(slot.target(), left, slot.difficulty(), slot.role()));
            }
        }
        return missing;
    }

    private static List<TrainingTargetSlot> limitSlots(List<TrainingTargetSlot> slots, int max) {
        List<TrainingTargetSlot> result = new ArrayList<>();
        int left = Math.max(0, max);
        for (TrainingTargetSlot slot : slots) {
            if (left <= 0) {
                break;
            }
            int count = Math.min(slot.count(), left);
            result.add(new TrainingTargetSlot(slot.target(), count, slot.difficulty(), slot.role()));
            left -= count;
        }
        return result;
    }

    private static int minItems(TrainingStage stage) {
        return switch (stage) {
            case MISTAKE -> 1;
            case RETEST -> 2;
            case BASELINE, DAILY -> 3;
        };
    }

    private static String key(String kpKey, String role) {
        return kpKey + "|" + (role == null ? ROLE_PRACTICE : role);
    }

    private static String titleOf(TrainingStage stage, List<TrainingTargetSlot> slots) {
        String name = slots.get(0).target().kpName();
        return switch (stage) {
            case BASELINE -> "基线测评 · 五维能力";
            case RETEST -> "阶段复测 · " + name;
            case MISTAKE -> "错题重练 · " + name;
            case DAILY -> "今日训练 · " + name
                    + (slots.size() > 1 ? " / " + slots.get(1).target().kpName() : "");
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "训练策略序列化失败");
        }
    }

    public record GenerationResult(List<TrainingItemDraft> items,
                                   String title,
                                   String dimensionFocus,
                                   String policyJson) {
    }
}
