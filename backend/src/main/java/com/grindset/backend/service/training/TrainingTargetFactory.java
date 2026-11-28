package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.config.TrainingProperties;
import com.grindset.backend.dto.WeaknessView;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.TrainingMistake;
import com.grindset.backend.entity.UserKpMastery;
import com.grindset.backend.mapper.QuestionBankMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.Numbers.intValue;

/**
 * 把不同来源（题库、面试弱项、掌握度、错题）统一转成训练目标。
 * 阶段策略只认识 TrainingTarget，不需要知道数据从哪来。
 */
@Component
public class TrainingTargetFactory {

    private static final Map<TrainingDimension, String> FALLBACK_NAMES = Map.of(
            TrainingDimension.BASICS, "基础知识",
            TrainingDimension.PROJECT, "项目经历",
            TrainingDimension.SYSTEM_DESIGN, "系统设计",
            TrainingDimension.COMMUNICATION, "表达结构",
            TrainingDimension.CODING, "编码基础");

    private final QuestionBankMapper bankMapper;
    private final MasteryService masteryService;
    private final TrainingProperties properties;

    public TrainingTargetFactory(QuestionBankMapper bankMapper,
                                 MasteryService masteryService,
                                 TrainingProperties properties) {
        this.bankMapper = bankMapper;
        this.masteryService = masteryService;
        this.properties = properties;
    }

    /** 基线：每个维度至少一个代表知识点，题库不足时用通用知识点兜底 */
    public List<TrainingTarget> baseline(long userId) {
        Map<TrainingDimension, String> byDimension = new LinkedHashMap<>();
        for (QuestionBank question : bankMapper.selectList(new QueryWrapper<QuestionBank>()
                .eq("status", 1).orderByAsc("id"))) {
            TrainingDimension dimension = TrainingDimensionResolver.of(question);
            if (byDimension.containsKey(dimension) || question.getKpNames() == null
                    || question.getKpNames().isEmpty()) {
                continue;
            }
            String name = question.getKpNames().get(0);
            if (name != null && !name.isBlank()) {
                byDimension.put(dimension, name);
            }
        }
        List<TrainingTarget> targets = new ArrayList<>();
        for (TrainingDimension dimension : TrainingDimension.values()) {
            String name = byDimension.getOrDefault(dimension, FALLBACK_NAMES.get(dimension));
            UserKpMastery mastery = masteryService.find(userId, KnowledgePointKeys.keyOf(name));
            int before = mastery == null ? properties.getMastery().getInitial()
                    : intValue(mastery.getMastery());
            targets.add(new TrainingTarget(KnowledgePointKeys.keyOf(name), name, dimension.name(),
                    2, before, "基线测评", null, false, false));
        }
        return targets;
    }

    public List<TrainingTarget> fromWeaknesses(long userId, List<WeaknessView> weaknesses) {
        List<TrainingTarget> targets = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (WeaknessView weakness : weaknesses) {
            UserKpMastery mastery = masteryService.find(userId, weakness.kpKey());
            int before = mastery == null ? properties.getMastery().getInitial()
                    : intValue(mastery.getMastery());
            boolean due = mastery != null && mastery.getNextReviewAt() != null
                    && !mastery.getNextReviewAt().isAfter(now);
            boolean stretch = before >= 40 && before < 75;
            targets.add(new TrainingTarget(weakness.kpKey(), weakness.kpName(),
                    TrainingDimension.from(weakness.dimension()).name(), weakness.severity(), before,
                    weakness.evidence(), weakness.advice(), due, stretch));
        }
        return targets;
    }

    public List<TrainingTarget> fromMastery(long userId, List<UserKpMastery> rows) {
        List<TrainingTarget> targets = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserKpMastery row : rows) {
            TrainingDimension dimension = TrainingDimension.from(row.getDimension());
            boolean due = row.getNextReviewAt() != null && !row.getNextReviewAt().isAfter(now);
            int before = intValue(row.getMastery());
            targets.add(new TrainingTarget(row.getKpKey(), row.getKpName(), dimension.name(),
                    severityOf(before), before, null, null, due,
                    before >= 40 && before < 75));
        }
        return targets;
    }

    public List<TrainingTarget> fromMistakes(long userId, List<TrainingMistake> mistakes) {
        List<TrainingTarget> targets = new ArrayList<>();
        for (TrainingMistake mistake : mistakes) {
            UserKpMastery mastery = masteryService.find(userId, mistake.getKpKey());
            int before = mastery == null ? properties.getMastery().getInitial()
                    : intValue(mastery.getMastery());
            targets.add(new TrainingTarget(mistake.getKpKey(), mistake.getKpName(),
                    TrainingDimension.from(mistake.getDimension()).name(), 3, before,
                    mistake.getComment(), mistake.getSuggestion(), false, false));
        }
        return targets;
    }

    private static int severityOf(int mastery) {
        if (mastery < 40) {
            return 3;
        }
        return mastery < 75 ? 2 : 1;
    }

}
