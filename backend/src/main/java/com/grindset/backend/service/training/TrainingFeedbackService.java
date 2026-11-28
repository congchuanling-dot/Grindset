package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.dto.SimilarQuestionView;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.mapper.QuestionBankMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 训练反馈素材：答错后推荐同知识点、难度接近的题，做到举一反三。
 */
@Service
public class TrainingFeedbackService {

    private final QuestionBankMapper bankMapper;

    public TrainingFeedbackService(QuestionBankMapper bankMapper) {
        this.bankMapper = bankMapper;
    }

    public List<SimilarQuestionView> similar(String kpKey, String domain,
                                             Long currentQuestionId, Integer difficulty,
                                             int limit) {
        if ((kpKey == null || kpKey.isBlank()) && (domain == null || domain.isBlank())) {
            return List.of();
        }
        int target = difficulty == null ? 2 : difficulty;
        List<QuestionBank> all = bankMapper.selectList(new QueryWrapper<QuestionBank>()
                .eq("status", 1));
        List<QuestionBank> exact = new ArrayList<>();
        List<QuestionBank> sameDomain = new ArrayList<>();
        for (QuestionBank question : all) {
            if (!QuestionKpMatcher.isText(question)) {
                continue;
            }
            if (currentQuestionId != null && currentQuestionId.equals(question.getId())) {
                continue;
            }
            if (QuestionKpMatcher.contains(question, kpKey)) {
                exact.add(question);
            } else if (domain != null && domain.equals(question.getDomain())) {
                sameDomain.add(question);
            }
        }
        List<QuestionBank> matched = exact.isEmpty() ? sameDomain : exact;
        matched.sort(Comparator
                .comparingInt((QuestionBank question) -> Math.abs(
                        (question.getDifficulty() == null ? 2 : question.getDifficulty()) - target))
                .thenComparingLong(question -> question.getId() == null ? 0L : question.getId()));
        List<SimilarQuestionView> result = new ArrayList<>();
        for (QuestionBank question : matched) {
            if (result.size() >= Math.max(1, limit)) {
                break;
            }
            result.add(new SimilarQuestionView(
                    String.valueOf(question.getId()), question.getTitle(),
                    question.getDomain(), question.getDifficulty()));
        }
        return result;
    }
}
