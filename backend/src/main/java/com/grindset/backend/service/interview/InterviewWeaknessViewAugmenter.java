package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.WeaknessView;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.service.training.WeaknessDiagnosisService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧报告兼容：报告 JSON 里没有弱项时，按题目评分即时补一份视图。
 * 不写库，避免读接口产生副作用；用户生成训练计划时再落库。
 */
@Component
public class InterviewWeaknessViewAugmenter {

    private final WeaknessDiagnosisService weaknessDiagnosisService;
    private final InterviewMessageMapper messageMapper;

    public InterviewWeaknessViewAugmenter(WeaknessDiagnosisService weaknessDiagnosisService,
                                          InterviewMessageMapper messageMapper) {
        this.weaknessDiagnosisService = weaknessDiagnosisService;
        this.messageMapper = messageMapper;
    }

    public InterviewDetail.ReportView augment(long userId, long sessionId,
                                              InterviewDetail.ReportView report,
                                              List<InterviewQuestion> questions) {
        if (report == null || (report.weaknesses() != null && !report.weaknesses().isEmpty())) {
            return report;
        }
        List<WeaknessView> weaknesses = weaknessDiagnosisService.listBySession(userId, sessionId);
        if (weaknesses.isEmpty()) {
            weaknesses = weaknessDiagnosisService.diagnoseFromQuestions(questions, lastAnswers(questions));
        }
        return new InterviewDetail.ReportView(report.overallScore(), report.dimensionScores(),
                report.weakPoints(), report.highlights(), report.advice(),
                report.jdMatchScore(), report.jdCoverage(), report.jdGaps(), report.coding(),
                weaknesses);
    }

    private Map<Long, String> lastAnswers(List<InterviewQuestion> questions) {
        Map<Long, String> result = new HashMap<>();
        if (questions == null || questions.isEmpty()) {
            return result;
        }
        List<Long> ids = questions.stream().map(InterviewQuestion::getId).toList();
        List<InterviewMessage> messages = messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                .in("question_id", ids)
                .orderByAsc("seq"));
        for (InterviewMessage message : messages) {
            if ("USER".equals(message.getRole()) && message.getContent() != null) {
                result.put(message.getQuestionId(), message.getContent());
            }
        }
        return result;
    }
}
