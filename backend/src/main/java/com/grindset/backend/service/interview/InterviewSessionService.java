package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.CreateInterviewRequest;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.mapper.ResumeMapper;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.service.ResumeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.service.interview.InterviewConstants.MAX_FOCUS;
import static com.grindset.backend.service.interview.InterviewConstants.QUESTION_COUNTS;
import static com.grindset.backend.service.interview.InterviewConstants.SESSION_ONGOING;
import static com.grindset.backend.service.interview.InterviewConstants.STYLES;
import static com.grindset.backend.service.interview.InterviewViews.format;
import static com.grindset.backend.service.interview.InterviewViews.intValue;

/**
 * 面试会话生命周期:创建、列表、详情、删除,以及会话归属校验。
 * 出题答题在 {@link InterviewTurnService},复盘在 {@link InterviewReportService}。
 */
@Service
public class InterviewSessionService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeService resumeService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewSessionService(InterviewSessionMapper sessionMapper,
                                   InterviewQuestionMapper questionMapper,
                                   InterviewMessageMapper messageMapper,
                                   ResumeMapper resumeMapper,
                                   ResumeService resumeService,
                                   SnowflakeIdGenerator idGenerator,
                                   TransactionTemplate transactionTemplate,
                                   ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 创建会话:校验简历归属与解析状态,不在这里出题 */
    public InterviewDetail create(long userId, CreateInterviewRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        long resumeId = parseId(request.getResumeId(), "简历 ID 不合法");

        String position = request.getPosition() == null ? "" : request.getPosition().strip();
        if (position.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择面试岗位");
        }
        if (position.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试岗位过长");
        }

        String style = request.getStyle() == null ? "" : request.getStyle().strip();
        if (!STYLES.contains(style)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试风格不合法");
        }

        String round = request.getRound() == null ? null : request.getRound().strip();
        if (round != null && round.isEmpty()) {
            round = null;
        }
        if (round != null && round.length() > 32) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试轮次过长");
        }

        List<String> focus = normalizeFocus(request.getFocus());

        int questionCount = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        if (!QUESTION_COUNTS.contains(questionCount)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "题数只能是 3 / 5 / 8");
        }

        ResumeDetail resume = resumeService.get(userId, resumeId);
        if (resume.parseStatus() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这份简历还没有解析成功,请先在简历管理页重新上传");
        }

        long sessionId = idGenerator.nextId();
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setResumeId(resumeId);
        session.setPosition(position);
        session.setStyle(style);
        session.setRound(round);
        session.setFocus(focus);
        session.setQuestionCount(questionCount);
        session.setCompletedQuestions(0);
        session.setStatus(SESSION_ONGOING);
        sessionMapper.insert(session);

        return detail(userId, sessionId);
    }

    /** 历史会话列表,最新 50 条 */
    public List<InterviewSummary> list(long userId, List<Long> filterResumeIds) {
        QueryWrapper<InterviewSession> wrapper = new QueryWrapper<InterviewSession>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 50");
        if (filterResumeIds != null && !filterResumeIds.isEmpty()) {
            if (filterResumeIds.size() > 50) {
                throw new BizException(ErrorCode.BAD_REQUEST, "简历筛选数量过多");
            }
            wrapper.in("resume_id", filterResumeIds);
        }
        List<InterviewSession> sessions = sessionMapper.selectList(wrapper);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> resumeIds = sessions.stream().map(InterviewSession::getResumeId).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (Resume resume : resumeMapper.selectBatchIds(resumeIds)) {
            names.put(resume.getId(), resume.getFileName());
        }

        List<InterviewSummary> result = new ArrayList<>(sessions.size());
        for (InterviewSession session : sessions) {
            result.add(new InterviewSummary(String.valueOf(session.getId()),
                    names.getOrDefault(session.getResumeId(), "已删除的简历"),
                    session.getPosition(), session.getStyle(), session.getRound(),
                    intValue(session.getQuestionCount()), intValue(session.getCompletedQuestions()),
                    intValue(session.getStatus()), session.getOverallScore(), format(session.getCreatedAt())));
        }
        return result;
    }

    /** 会话详情,刷新页面后靠它恢复全部线程 */
    public InterviewDetail detail(long userId, long id) {
        InterviewSession session = requireOwned(userId, id);
        return buildDetail(session);
    }

    /** 删除会话及全部线程消息 */
    public void delete(long userId, long id) {
        InterviewSession session = requireOwned(userId, id);
        transactionTemplate.executeWithoutResult(status -> {
            List<InterviewQuestion> questions = questionMapper.selectList(
                    new QueryWrapper<InterviewQuestion>().eq("session_id", id));
            if (!questions.isEmpty()) {
                List<Long> questionIds = questions.stream().map(InterviewQuestion::getId).toList();
                messageMapper.delete(new QueryWrapper<InterviewMessage>().in("question_id", questionIds));
                questionMapper.delete(new QueryWrapper<InterviewQuestion>().eq("session_id", id));
            }
            sessionMapper.deleteById(session.getId());
        });
    }

    /** 归属校验失败统一 404,避免泄露会话是否存在 */
    public InterviewSession requireOwned(long userId, long id) {
        InterviewSession session = sessionMapper.selectById(id);
        if (session == null || session.getUserId() == null || session.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    private InterviewDetail buildDetail(InterviewSession session) {
        Resume resume = resumeMapper.selectById(session.getResumeId());
        String resumeFileName = resume == null ? "已删除的简历" : resume.getFileName();

        List<InterviewQuestion> questions = questionMapper.selectList(new QueryWrapper<InterviewQuestion>()
                .eq("session_id", session.getId())
                .orderByAsc("seq"));
        Map<Long, List<InterviewMessage>> messagesByQuestion = new LinkedHashMap<>();
        if (!questions.isEmpty()) {
            List<Long> questionIds = questions.stream().map(InterviewQuestion::getId).toList();
            List<InterviewMessage> messages = messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                    .in("question_id", questionIds)
                    .orderByAsc("seq"));
            for (InterviewMessage message : messages) {
                messagesByQuestion.computeIfAbsent(message.getQuestionId(), key -> new ArrayList<>()).add(message);
            }
        }

        List<InterviewDetail.QuestionView> questionViews = new ArrayList<>(questions.size());
        for (InterviewQuestion question : questions) {
            List<InterviewDetail.MessageView> messages = messagesByQuestion
                    .getOrDefault(question.getId(), List.of()).stream()
                    .map(InterviewViews::toView)
                    .toList();
            questionViews.add(new InterviewDetail.QuestionView(String.valueOf(question.getId()),
                    intValue(question.getSeq()), intValue(question.getStatus()),
                    intValue(question.getFollowUpCount()), question.getScore(),
                    question.getKnowledgePoints() == null ? List.of() : question.getKnowledgePoints(),
                    question.getComment(), question.getSuggestion(), messages));
        }

        InterviewDetail.ReportView report = null;
        if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
            report = InterviewViews.parseReport(objectMapper, session.getReportJson());
        }

        return new InterviewDetail(String.valueOf(session.getId()), String.valueOf(session.getResumeId()),
                resumeFileName, session.getPosition(), session.getStyle(), session.getRound(),
                session.getFocus() == null ? List.of() : session.getFocus(),
                intValue(session.getQuestionCount()), intValue(session.getCompletedQuestions()),
                intValue(session.getStatus()), session.getOverallScore(),
                format(session.getCreatedAt()), questionViews, report);
    }

    private List<String> normalizeFocus(List<String> focus) {
        List<String> result = new ArrayList<>();
        if (focus == null) {
            return result;
        }
        for (String item : focus) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (result.size() >= MAX_FOCUS) {
                throw new BizException(ErrorCode.BAD_REQUEST, "侧重方向最多选 " + MAX_FOCUS + " 个");
            }
            String value = item.strip();
            if (value.length() > 32) {
                throw new BizException(ErrorCode.BAD_REQUEST, "侧重方向过长");
            }
            result.add(value);
        }
        return result;
    }

    private static long parseId(String value, String message) {
        try {
            return Long.parseLong(value == null ? "" : value.strip());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
