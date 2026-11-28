package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.dto.TrainingDashboard;
import com.grindset.backend.entity.InterviewWeakness;
import com.grindset.backend.entity.TrainingMasterySnapshot;
import com.grindset.backend.entity.TrainingPlan;
import com.grindset.backend.entity.UserKpMastery;
import com.grindset.backend.mapper.InterviewWeaknessMapper;
import com.grindset.backend.mapper.TrainingMasterySnapshotMapper;
import com.grindset.backend.mapper.TrainingPlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.Numbers.intValue;
import static com.grindset.backend.common.constant.TimeFormats.format;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_PENDING;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_RUNNING;

/**
 * 训练仪表盘：五维雷达、总体 readiness、成长曲线、弱项地图、错题摘要。
 */
@Service
public class TrainingAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(TrainingAnalyticsService.class);

    private final MasteryService masteryService;
    private final TrainingPlanMapper planMapper;
    private final TrainingMasterySnapshotMapper snapshotMapper;
    private final InterviewWeaknessMapper weaknessMapper;
    private final MistakeService mistakeService;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public TrainingAnalyticsService(MasteryService masteryService,
                                    TrainingPlanMapper planMapper,
                                    TrainingMasterySnapshotMapper snapshotMapper,
                                    InterviewWeaknessMapper weaknessMapper,
                                    MistakeService mistakeService,
                                    SnowflakeIdGenerator idGenerator,
                                    ObjectMapper objectMapper) {
        this.masteryService = masteryService;
        this.planMapper = planMapper;
        this.snapshotMapper = snapshotMapper;
        this.weaknessMapper = weaknessMapper;
        this.mistakeService = mistakeService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public TrainingDashboard dashboard(long userId) {
        Map<TrainingDimension, Integer> dimensions = masteryService.dimensionMastery(userId);
        int readiness = (int) Math.round(dimensions.values().stream()
                .mapToInt(Integer::intValue).average().orElse(50));
        List<TrainingDashboard.DimensionView> dimensionViews = new ArrayList<>();
        for (TrainingDimension dimension : TrainingDimension.values()) {
            int value = dimensions.getOrDefault(dimension, 50);
            dimensionViews.add(new TrainingDashboard.DimensionView(
                    dimension.name(), dimension.label(), value,
                    masteryService.dimensionDelta(userId, dimension, 7)));
        }

        List<UserKpMastery> weakRows = masteryService.weakItems(userId, 8).stream()
                .filter(item -> item.getEliminatedAt() == null)
                .toList();
        Map<String, String> evidence = evidenceOf(userId, weakRows);
        List<TrainingDashboard.WeaknessItem> weaknesses = new ArrayList<>();
        for (UserKpMastery item : weakRows) {
            TrainingDimension dimension = TrainingDimension.from(item.getDimension());
            weaknesses.add(new TrainingDashboard.WeaknessItem(
                    item.getKpKey(), item.getKpName(), dimension.name(), dimension.label(),
                    intValue(item.getMastery()), item.getLevel(),
                    evidence.get(item.getKpKey()), format(item.getNextReviewAt()),
                    false));
        }

        TrainingPlan active = planMapper.selectOne(new QueryWrapper<TrainingPlan>()
                .eq("user_id", userId)
                .in("status", PLAN_PENDING, PLAN_RUNNING)
                .orderByDesc("created_at")
                .last("LIMIT 1"));
        TrainingDashboard.ActivePlan activePlan = active == null ? null
                : new TrainingDashboard.ActivePlan(String.valueOf(active.getId()), active.getTitle(),
                active.getStage(), intValue(active.getCompletedCount()), intValue(active.getItemCount()));

        return new TrainingDashboard(
                readiness, readinessDelta7d(userId, readiness), masteryService.hasData(userId),
                dimensionViews, growth(userId),
                weaknesses, masteryService.dueCount(userId), mistakeService.openCount(userId),
                activePlan, mistakeService.list(userId, 0, 5));
    }

    /** 每天首次打开仪表盘写一条快照，用于成长曲线 */
    public void snapshot(long userId, Map<TrainingDimension, Integer> dimensions) {
        LocalDate today = LocalDate.now();
        int readiness = (int) Math.round(dimensions.values().stream()
                .mapToInt(Integer::intValue).average().orElse(50));
        Map<String, Integer> json = new LinkedHashMap<>();
        dimensions.forEach((key, value) -> json.put(key.name(), value));
        String dimensionJson = toJson(json);
        TrainingMasterySnapshot existing = snapshotMapper.selectOne(
                new QueryWrapper<TrainingMasterySnapshot>()
                        .eq("user_id", userId)
                        .eq("snapshot_date", today)
                        .last("LIMIT 1"));
        if (existing == null) {
            TrainingMasterySnapshot row = new TrainingMasterySnapshot();
            row.setId(idGenerator.nextId());
            row.setUserId(userId);
            row.setSnapshotDate(today);
            row.setOverallMastery(readiness);
            row.setDimensionJson(dimensionJson);
            row.setCreatedAt(LocalDateTime.now());
            snapshotMapper.insert(row);
        } else {
            existing.setOverallMastery(readiness);
            existing.setDimensionJson(dimensionJson);
            snapshotMapper.updateById(existing);
        }
    }

    public List<TrainingDashboard.GrowthPoint> growth(long userId) {
        List<TrainingMasterySnapshot> rows = snapshotMapper.selectList(
                new QueryWrapper<TrainingMasterySnapshot>()
                        .eq("user_id", userId)
                        .ge("snapshot_date", LocalDate.now().minusDays(13))
                        .orderByAsc("snapshot_date")
                        .last("LIMIT 14"));
        List<TrainingDashboard.GrowthPoint> result = new ArrayList<>(rows.size());
        for (TrainingMasterySnapshot row : rows) {
            result.add(new TrainingDashboard.GrowthPoint(
                    row.getSnapshotDate() == null ? "" : row.getSnapshotDate().toString(),
                    intValue(row.getOverallMastery())));
        }
        return result;
    }

    private int readinessDelta7d(long userId, int readiness) {
        TrainingMasterySnapshot base = snapshotMapper.selectOne(
                new QueryWrapper<TrainingMasterySnapshot>()
                        .eq("user_id", userId)
                        .le("snapshot_date", LocalDate.now().minusDays(7))
                        .orderByDesc("snapshot_date")
                        .last("LIMIT 1"));
        return base == null ? 0 : readiness - intValue(base.getOverallMastery());
    }

    private Map<String, String> evidenceOf(long userId, List<UserKpMastery> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> kpKeys = rows.stream().map(UserKpMastery::getKpKey).toList();
        List<InterviewWeakness> weaknessRows = weaknessMapper.selectList(
                new QueryWrapper<InterviewWeakness>()
                        .eq("user_id", userId)
                        .in("kp_key", kpKeys)
                        .orderByDesc("created_at"));
        Map<String, String> result = new LinkedHashMap<>();
        for (InterviewWeakness row : weaknessRows) {
            result.putIfAbsent(row.getKpKey(), row.getEvidence());
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("维度快照序列化失败", e);
            return "{}";
        }
    }
}
