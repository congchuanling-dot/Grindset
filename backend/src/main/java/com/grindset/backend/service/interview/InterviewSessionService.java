package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.InterviewQuestionType;
import com.grindset.backend.common.InterviewStyle;
import com.grindset.backend.common.JobLevel;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.CreateInterviewRequest;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.dto.JdProfile;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.mapper.InterviewWeaknessMapper;
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

import static com.grindset.backend.service.interview.InterviewConstants.DEFAULT_CODING_TIME_LIMIT_SEC;
import static com.grindset.backend.service.interview.InterviewConstants.MAX_FOCUS;
import static com.grindset.backend.service.interview.InterviewConstants.QUESTION_COUNTS;

import static com.grindset.backend.common.constant.InterviewSessionStatus.ONGOING;
import static com.grindset.backend.service.interview.InterviewViews.format;
import static com.grindset.backend.common.Numbers.intValue;

/**
 * 面试会话生命周期:创建、列表、详情、删除,以及会话归属校验。
 * 出题答题在 {@link InterviewTurnService},复盘在 {@link InterviewReportService}。
 */
@Service
public class InterviewSessionService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewWeaknessMapper weaknessMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeService resumeService;
    private final InterviewJdService jdService;
    private final InterviewWeaknessViewAugmenter weaknessViewAugmenter;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewSessionService(InterviewSessionMapper sessionMapper,
                                   InterviewQuestionMapper questionMapper,
                                   InterviewMessageMapper messageMapper,
                                   InterviewWeaknessMapper weaknessMapper,
                                   ResumeMapper resumeMapper,
                                   ResumeService resumeService,
                                   InterviewJdService jdService,
                                   InterviewWeaknessViewAugmenter weaknessViewAugmenter,
                                   SnowflakeIdGenerator idGenerator,
                                   TransactionTemplate transactionTemplate,
                                   ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.weaknessMapper = weaknessMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
        this.jdService = jdService;
        this.weaknessViewAugmenter = weaknessViewAugmenter;
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

        String company = jdService.normalizeCompany(request.getCompany());
        String jdText = jdService.normalizeJdText(request.getJdText());

        String position = request.getPosition() == null ? "" : request.getPosition().strip();
        if (position.isEmpty() && jdText == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择面试岗位");
        }
        if (position.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试岗位过长");
        }

        InterviewStyle style = InterviewStyle.from(request.getStyle());
        if (style == null) {
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

        JobLevel jobLevel = JobLevel.from(request.getJobLevel());
        if (request.getJobLevel() != null && !request.getJobLevel().isBlank() && jobLevel == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "岗位级别不合法");
        }

        int questionCount = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        if (!QUESTION_COUNTS.contains(questionCount)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "题数只能是 3 / 5 / 8");
        }

        ResumeDetail resume = resumeService.get(userId, resumeId);
        if (resume.parseStatus() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这份简历还没有解析成功,请先在简历管理页重新上传");
        }

        if (jobLevel == null) {
            jobLevel = JobLevel.fromCandidateType(resume.candidateType());
        }
        // 前端预览过就直接用回传画像,避免创建时再调一次模型;否则现场解析
        JdProfile jdProfile = jdText == null ? null
                : jdService.normalizeProvided(request.getJdProfile(), position, company, jobLevel);
        if (jdProfile == null) {
            jdProfile = jdService.parseProfile(userId, resumeId,
                    position.isEmpty() ? "目标岗位" : position, jdText, company, jobLevel);
        }
        if (position.isEmpty() && jdProfile != null && jdProfile.title() != null) {
            position = jdProfile.title();
        }
        if (position.isEmpty()) {
            position = "目标岗位";
        }
        if (company == null && jdProfile != null) {
            company = jdProfile.company();
        }

        long sessionId = idGenerator.nextId();
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setResumeId(resumeId);
        session.setPosition(position);
        session.setCompany(company);
        session.setJobLevel(jobLevel);
        session.setJdText(jdText);
        session.setJdProfileJson(jdService.toJson(jdProfile));
        session.setStyle(style);
        session.setRound(round);
        session.setFocus(focus);
        session.setQuestionCount(questionCount);
        // 产品约定:非 HR 面必须包含一道手撕代码,HR 面自动关闭,不信任前端开关
        boolean hrRound = round != null && round.contains("HR");
        session.setCodingEnabled(hrRound ? 0 : 1);
        session.setCodingTimeLimitSec(normalizeCodingLimit(request.getCodingTimeLimitSec()));
        session.setCompletedQuestions(0);
        session.setStatus(ONGOING);
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
                    session.getPosition(), session.getCompany(), styleCode(session), session.getRound(),
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
            weaknessMapper.delete(new QueryWrapper<com.grindset.backend.entity.InterviewWeakness>()
                    .eq("user_id", userId)
                    .eq("session_id", id));
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
                    intValue(question.getSeq()),
                    (question.getQuestionType() == null
                            ? InterviewQuestionType.VERBAL : question.getQuestionType()).name(),
                    intValue(question.getStatus()),
                    intValue(question.getFollowUpCount()), question.getScore(),
                    question.getKnowledgePoints() == null ? List.of() : question.getKnowledgePoints(),
                    question.getCompetency(),
                    InterviewViews.jdGapOf(objectMapper, question.getEvaluationJson()),
                    question.getComment(), question.getSuggestion(),
                    question.getCodingQuestionId() == null ? null : String.valueOf(question.getCodingQuestionId()),
                    question.getCodingTitle(), question.getJudgeStatus(),
                    question.getPassed(), question.getTotal(),
                    question.getCodingSubmissionId() == null ? null : String.valueOf(question.getCodingSubmissionId()),
                    format(question.getDeadlineAt()), messages));
        }

        InterviewDetail.ReportView report = null;
        if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
            report = InterviewViews.parseReport(objectMapper, session.getReportJson());
            report = weaknessViewAugmenter.augment(session.getUserId(), session.getId(), report, questions);
        }

        JdProfile jdProfile = jdService.profileOf(session);
        boolean jdEnabled = session.getJdText() != null && !session.getJdText().isBlank();
        return new InterviewDetail(String.valueOf(session.getId()), String.valueOf(session.getResumeId()),
                resumeFileName, session.getPosition(), session.getCompany(),
                session.getJobLevel() == null ? null : session.getJobLevel().code(),
                jdEnabled, jdProfile,
                styleCode(session), session.getRound(),
                session.getFocus() == null ? List.of() : session.getFocus(),
                intValue(session.getQuestionCount()),
                intValue(session.getCodingEnabled()) == 1,
                positive(session.getCodingTimeLimitSec(), DEFAULT_CODING_TIME_LIMIT_SEC),
                intValue(session.getCompletedQuestions()),
                intValue(session.getStatus()), session.getOverallScore(),
                format(session.getCreatedAt()), questionViews, report);
    }

    private static String styleCode(InterviewSession session) {
        return session.getStyle() == null ? null : session.getStyle().code();
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

    private static int normalizeCodingLimit(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_CODING_TIME_LIMIT_SEC;
        }
        return Math.min(7200, Math.max(300, value));
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static long parseId(String value, String message) {
        try {
            return Long.parseLong(value == null ? "" : value.strip());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
