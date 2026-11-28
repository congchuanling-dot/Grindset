package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.UserQuestionProgress;
import com.grindset.backend.mapper.QuestionBankMapper;
import com.grindset.backend.mapper.UserQuestionProgressMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库题源：优先未做、其次错题，最后已做；同优先级按难度与 ID 排序。
 * 只选 TEXT 题；代码题由后续的代码 provider 扩展。
 */
@Component
public class BankTrainingItemProvider implements TrainingItemProvider {

    private final QuestionBankMapper bankMapper;
    private final UserQuestionProgressMapper progressMapper;

    public BankTrainingItemProvider(QuestionBankMapper bankMapper,
                                    UserQuestionProgressMapper progressMapper) {
        this.bankMapper = bankMapper;
        this.progressMapper = progressMapper;
    }

    @Override
    public List<TrainingItemDraft> provide(TrainingGenerationContext context,
                                           List<TrainingTargetSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<QuestionBank> all = bankMapper.selectList(new QueryWrapper<QuestionBank>()
                .eq("status", 1));
        Map<Long, UserQuestionProgress> progress = loadProgress(context.userId());
        LocalDateTime recentCutoff = LocalDateTime.now().minusDays(7);

        List<TrainingItemDraft> result = new ArrayList<>();
        for (TrainingTargetSlot slot : slots) {
            if (slot.count() <= 0) {
                continue;
            }
            int picked = 0;
            int difficulty = slot.difficulty();
            for (QuestionBank question : all.stream()
                    .filter(QuestionKpMatcher::isText)
                    .filter(item -> QuestionKpMatcher.contains(item, slot.target().kpKey()))
                    .filter(item -> item.getId() != null
                            && !context.excludedQuestionIds().contains(item.getId()))
                    .sorted(questionComparator(progress, recentCutoff, difficulty))
                    .toList()) {
                result.add(new TrainingItemDraft(slot.target().kpKey(), slot.target().kpName(),
                        slot.target().dimension(), slot.role(), "TEXT", question.getDifficulty(),
                        question.getId(), null, question.getTitle()));
                context.excludedQuestionIds().add(question.getId());
                picked++;
                if (picked >= slot.count()) {
                    break;
                }
            }
        }
        return result;
    }

    private Map<Long, UserQuestionProgress> loadProgress(long userId) {
        Map<Long, UserQuestionProgress> map = new HashMap<>();
        for (UserQuestionProgress item : progressMapper.selectList(
                new QueryWrapper<UserQuestionProgress>().eq("user_id", userId))) {
            map.put(item.getQuestionId(), item);
        }
        return map;
    }

    private static Comparator<QuestionBank> questionComparator(
            Map<Long, UserQuestionProgress> progress, LocalDateTime recentCutoff, int targetDifficulty) {
        return Comparator
                .comparingInt((QuestionBank question) -> rank(progress.get(question.getId()), recentCutoff))
                .thenComparingInt(question -> Math.abs(
                        (question.getDifficulty() == null ? 2 : question.getDifficulty()) - targetDifficulty))
                .thenComparingInt(question -> question.getDifficulty() == null ? 2 : question.getDifficulty())
                .thenComparingLong(question -> question.getId() == null ? 0L : question.getId());
    }

    private static int rank(UserQuestionProgress progress, LocalDateTime recentCutoff) {
        if (progress == null) {
            return 0;
        }
        int recent = progress.getLastAttemptAt() != null
                && progress.getLastAttemptAt().isAfter(recentCutoff) ? 10 : 0;
        return (progress.getStatus() != null && progress.getStatus() == 2 ? 1 : 2) + recent;
    }
}
