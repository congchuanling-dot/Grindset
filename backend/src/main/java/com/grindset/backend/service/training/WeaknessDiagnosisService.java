package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.InterviewQuestionType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.config.TrainingProperties;
import com.grindset.backend.dto.InterviewReport;
import com.grindset.backend.dto.WeaknessView;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewWeakness;
import com.grindset.backend.mapper.InterviewWeaknessMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 弱项诊断：把复盘报告的弱项对齐到具体题目的知识点，落库并更新掌握度。
 *
 * <p>关键原则：知识点身份以题目记录为准，报告文本只提供展示与排序。
 * 模型写出无法对齐的知识点时会被丢弃，不让它凭空创造知识点。</p>
 */
@Service
public class WeaknessDiagnosisService {

    private final InterviewWeaknessMapper weaknessMapper;
    private final MasteryService masteryService;
    private final TrainingProperties properties;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public WeaknessDiagnosisService(InterviewWeaknessMapper weaknessMapper,
                                    MasteryService masteryService,
                                    TrainingProperties properties,
                                    SnowflakeIdGenerator idGenerator,
                                    TransactionTemplate transactionTemplate) {
        this.weaknessMapper = weaknessMapper;
        this.masteryService = masteryService;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 解析并持久化弱项；同时把本场所有已评分题的知识点写回掌握度。
     * 返回最终展示用弱项，保证报告视图与数据库一致。
     */
    public List<WeaknessView> resolveAndPersist(long userId, long sessionId,
                                                List<InterviewQuestion> questions,
                                                InterviewReport report,
                                                Map<Long, String> lastAnswers) {
        List<Resolved> resolved = resolve(report, questions, lastAnswers);
        transactionTemplate.executeWithoutResult(status -> {
            weaknessMapper.delete(new QueryWrapper<InterviewWeakness>()
                    .eq("user_id", userId)
                    .eq("session_id", sessionId));
            LocalDateTime now = LocalDateTime.now();
            for (Resolved item : resolved) {
                InterviewWeakness row = new InterviewWeakness();
                row.setId(idGenerator.nextId());
                row.setUserId(userId);
                row.setSessionId(sessionId);
                row.setQuestionId(item.question() == null ? null : item.question().getId());
                row.setQuestionSeq(item.question() == null ? null : item.question().getSeq());
                row.setKpKey(item.kpKey());
                row.setKpName(item.kpName());
                row.setDimension(item.dimension());
                row.setDomain(item.domain());
                row.setSeverity(item.severity());
                row.setEvidence(truncate(item.evidence(), 500));
                row.setAdvice(truncate(item.advice(), 200));
                row.setSource("REPORT");
                row.setCreatedAt(now);
                weaknessMapper.insert(row);
            }
            masteryService.observeInterviewQuestions(userId, sessionId, questions);
            if (report != null && report.dimensionScores() != null) {
                report.dimensionScores().forEach((name, score) -> {
                    if (score != null) {
                        masteryService.observeDimension(userId,
                                TrainingDimension.ofReportDimension(name), score, "INTERVIEW");
                    }
                });
            }
        });
        return resolved.stream().map(this::toView).toList();
    }

    /** 旧报告或缺失落库时，按题目评分即时诊断一份，不写库 */
    public List<WeaknessView> diagnoseFromQuestions(List<InterviewQuestion> questions,
                                                    Map<Long, String> lastAnswers) {
        return resolve(null, questions, lastAnswers).stream().map(this::toView).toList();
    }

    public List<WeaknessView> listBySession(long userId, long sessionId) {
        List<InterviewWeakness> rows = weaknessMapper.selectList(new QueryWrapper<InterviewWeakness>()
                .eq("user_id", userId)
                .eq("session_id", sessionId)
                .orderByDesc("severity")
                .orderByAsc("id"));
        return rows.stream().map(this::toView).toList();
    }

    /** 跨场次最近弱项，按知识点去重，用于日常训练目标 */
    public List<WeaknessView> listLatest(long userId, int limit) {
        List<InterviewWeakness> rows = weaknessMapper.selectList(new QueryWrapper<InterviewWeakness>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, Math.min(limit * 3, 60))));
        Map<String, WeaknessView> result = new LinkedHashMap<>();
        for (InterviewWeakness row : rows) {
            result.putIfAbsent(row.getKpKey(), toView(row));
            if (result.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(result.values());
    }

    /** 计划生成时优先用已落库弱项；没有则即时诊断并落库 */
    public List<WeaknessView> listOrDiagnose(long userId, long sessionId,
                                             List<InterviewQuestion> questions,
                                             InterviewReport report,
                                             Map<Long, String> lastAnswers) {
        List<WeaknessView> existing = listBySession(userId, sessionId);
        if (!existing.isEmpty()) {
            return existing;
        }
        return resolveAndPersist(userId, sessionId, questions, report, lastAnswers);
    }

    public void deleteBySession(long userId, long sessionId) {
        weaknessMapper.delete(new QueryWrapper<InterviewWeakness>()
                .eq("user_id", userId)
                .eq("session_id", sessionId));
    }

    private List<Resolved> resolve(InterviewReport report, List<InterviewQuestion> questions,
                                   Map<Long, String> lastAnswers) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        Map<Integer, InterviewQuestion> bySeq = new HashMap<>();
        Map<String, KpRef> byKey = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            if (question.getSeq() != null) {
                bySeq.put(question.getSeq(), question);
            }
            if (question.getKnowledgePoints() == null) {
                continue;
            }
            for (String point : question.getKnowledgePoints()) {
                String key = KnowledgePointKeys.keyOf(point);
                if (!key.isEmpty()) {
                    byKey.putIfAbsent(key, new KpRef(KnowledgePointKeys.displayName(point), question));
                }
            }
        }

        List<Resolved> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (report != null && report.weaknesses() != null) {
            for (InterviewReport.Weakness item : report.weaknesses()) {
                if (item == null) {
                    continue;
                }
                String name = KnowledgePointKeys.displayName(item.name());
                if (name.isEmpty()) {
                    continue;
                }
                KpRef ref = byKey.get(KnowledgePointKeys.keyOf(name));
                if (ref == null && item.questionSeq() != null) {
                    InterviewQuestion question = bySeq.get(item.questionSeq());
                    ref = firstKp(question);
                }
                if (ref == null) {
                    continue;
                }
                String key = KnowledgePointKeys.keyOf(ref.kpName());
                if (key.isEmpty() || !seen.add(key)) {
                    continue;
                }
                InterviewQuestion question = ref.question();
                int severity = item.severity() == null ? 2 : Math.max(1, Math.min(3, item.severity()));
                result.add(new Resolved(question, key, ref.kpName(),
                        TrainingDimensionResolver.of(question).name(), null, severity,
                        firstNonBlank(item.evidence(), answerOf(lastAnswers, question),
                                question == null ? null : question.getComment()),
                        firstNonBlank(item.advice(), question == null ? null : question.getSuggestion()),
                        normalizeMode(item.suggestedMode(), question)));
                if (result.size() >= properties.getMaxWeaknesses()) {
                    break;
                }
            }
        }

        if (result.isEmpty()) {
            List<InterviewQuestion> low = new ArrayList<>();
            for (InterviewQuestion question : questions) {
                if (question.getScore() != null && question.getScore() <= 2) {
                    low.add(question);
                }
            }
            // 没有明显答崩的题时，退一步取最低分（<=3）的题，保证仍有可练的点
            if (low.isEmpty()) {
                for (InterviewQuestion question : questions) {
                    if (question.getScore() != null && question.getScore() <= 3) {
                        low.add(question);
                    }
                }
            }
            low.sort(Comparator.comparingInt((InterviewQuestion q) -> q.getScore() == null ? 5 : q.getScore())
                    .thenComparing(Comparator.comparingInt(
                            (InterviewQuestion q) -> q.getSeq() == null ? 0 : q.getSeq()).reversed()));
            for (InterviewQuestion question : low) {
                KpRef ref = firstKp(question);
                if (ref == null || !seen.add(ref.kpKey())) {
                    continue;
                }
                int score = question.getScore() == null ? 1 : question.getScore();
                result.add(new Resolved(question, ref.kpKey(), ref.kpName(),
                        TrainingDimensionResolver.of(question).name(), null,
                        Math.max(1, Math.min(3, 5 - score)),
                        firstNonBlank(answerOf(lastAnswers, question), question.getComment()),
                        question.getSuggestion(),
                        question.getQuestionType() == InterviewQuestionType.CODING ? "CODING" : "ORAL"));
                if (result.size() >= properties.getMaxWeaknesses()) {
                    break;
                }
            }
        }
        return result;
    }

    private static KpRef firstKp(InterviewQuestion question) {
        if (question == null || question.getKnowledgePoints() == null
                || question.getKnowledgePoints().isEmpty()) {
            return null;
        }
        for (String point : question.getKnowledgePoints()) {
            String name = KnowledgePointKeys.displayName(point);
            String key = KnowledgePointKeys.keyOf(name);
            if (!key.isEmpty()) {
                return new KpRef(name, question);
            }
        }
        return null;
    }

    private static String normalizeMode(String mode, InterviewQuestion question) {
        if (question != null && question.getQuestionType() == InterviewQuestionType.CODING) {
            return "CODING";
        }
        String value = mode == null ? "" : mode.strip().toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "TEXT", "CODING", "ORAL" -> value;
            default -> "ORAL";
        };
    }

    private static String answerOf(Map<Long, String> lastAnswers, InterviewQuestion question) {
        if (lastAnswers == null || question == null || question.getId() == null) {
            return null;
        }
        return lastAnswers.get(question.getId());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private WeaknessView toView(Resolved item) {
        return new WeaknessView(item.kpKey(), item.kpName(), item.domain(),
                item.dimension(), TrainingDimension.from(item.dimension()).label(),
                item.severity(), truncate(item.evidence(), 500), truncate(item.advice(), 200),
                item.question() == null ? null : item.question().getSeq(),
                item.question() == null || item.question().getId() == null
                        ? null : String.valueOf(item.question().getId()));
    }

    private WeaknessView toView(InterviewWeakness row) {
        return new WeaknessView(row.getKpKey(), row.getKpName(), row.getDomain(),
                row.getDimension(), TrainingDimension.from(row.getDimension()).label(),
                row.getSeverity() == null ? 2 : row.getSeverity(),
                row.getEvidence(), row.getAdvice(), row.getQuestionSeq(),
                row.getQuestionId() == null ? null : String.valueOf(row.getQuestionId()));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private record KpRef(String kpName, InterviewQuestion question) {
        String kpKey() {
            return KnowledgePointKeys.keyOf(kpName);
        }
    }

    private record Resolved(InterviewQuestion question, String kpKey, String kpName,
                            String dimension, String domain, int severity,
                            String evidence, String advice, String mode) {
    }
}
