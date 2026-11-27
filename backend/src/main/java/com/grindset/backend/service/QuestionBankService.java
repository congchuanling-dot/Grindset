package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.JudgeMode;
import com.grindset.backend.common.QuestionSource;
import com.grindset.backend.common.QuestionType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.BankEvaluation;
import com.grindset.backend.dto.QuestionAttemptResult;
import com.grindset.backend.dto.QuestionBankDetail;
import com.grindset.backend.dto.QuestionBankFacets;
import com.grindset.backend.dto.QuestionBankImportRequest;
import com.grindset.backend.dto.QuestionBankImportResult;
import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.dto.QuestionNeighbors;
import com.grindset.backend.entity.QuestionAttempt;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.QuestionTestCase;
import com.grindset.backend.entity.UserQuestionProgress;
import com.grindset.backend.mapper.QuestionAttemptMapper;
import com.grindset.backend.mapper.QuestionBankMapper;
import com.grindset.backend.mapper.QuestionTestCaseMapper;
import com.grindset.backend.mapper.UserQuestionProgressMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.grindset.backend.common.constant.DisplayLimits.MAX_PREVIEW_CHARS;
import static com.grindset.backend.common.constant.InputLimits.MAX_ANSWER_LENGTH;
import static com.grindset.backend.common.constant.MasteryRules.WRONG_SCORE_MAX;
import static com.grindset.backend.common.constant.QuestionBankStatus.ONLINE;
import static com.grindset.backend.common.constant.QuestionProgressStatus.DONE;
import static com.grindset.backend.common.constant.QuestionProgressStatus.WRONG;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 题库第一版:列表筛选、题目详情、AI 评分作答、做题进度与错题联动。
 *
 * <p>题目量不大,列表在内存里做筛选和分页;模型调用放在事务外,
 * 错题收录与掌握度回写都是 best-effort 旁路,失败只记日志。</p>
 */
@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    private static final int PAGE_SIZE_DEFAULT = 20;
    private static final int PAGE_SIZE_MAX = 100;
    private static final int MIN_ANSWER_LENGTH = 10;
    private static final int ATTEMPT_HISTORY_LIMIT = 20;
    private static final int TITLE_MAX = 60;

    private static final Set<String> STATUS_FILTERS = Set.of("TODO", "DONE", "WRONG");

    private final QuestionBankMapper bankMapper;
    private final QuestionTestCaseMapper testCaseMapper;
    private final UserQuestionProgressMapper progressMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final InterviewLlmService llmService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 单实例内按"用户 + 题目"串行化,防止连点重复提交 */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public QuestionBankService(QuestionBankMapper bankMapper,
                               QuestionTestCaseMapper testCaseMapper,
                               UserQuestionProgressMapper progressMapper,
                               QuestionAttemptMapper attemptMapper,
                               InterviewLlmService llmService,
                               SnowflakeIdGenerator idGenerator,
                               TransactionTemplate transactionTemplate) {
        this.bankMapper = bankMapper;
        this.testCaseMapper = testCaseMapper;
        this.progressMapper = progressMapper;
        this.attemptMapper = attemptMapper;
        this.llmService = llmService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------- 列表与统计 ----------

    /** 题库列表;summary 按当前模式统计,items 按领域/题型/难度/状态/关键词筛选后分页 */
    public QuestionBankView list(long userId, String domain, String type, Integer difficulty, String status,
                                 String keyword, String judgeMode, int page, int size) {
        List<QuestionBank> all = allQuestions();
        JudgeMode modeFilter = normalizeJudgeMode(judgeMode);
        List<QuestionBank> scoped = scopeByMode(all, modeFilter);
        Map<Long, UserQuestionProgress> progress = loadProgress(userId);
        QuestionBankView.Summary summary = summarize(scoped, progress);

        String domainFilter = Texts.blankToNull(domain);
        QuestionType typeFilter = normalizeType(type);
        Integer difficultyFilter = difficulty == null ? null : clamp(difficulty, 1, 3);
        String statusFilter = normalizeStatus(status);
        String keywordFilter = Texts.blankToNull(keyword);

        List<QuestionBank> matched = scoped.stream()
                .filter(q -> domainFilter == null || matchesDomain(q, domainFilter))
                .filter(q -> typeFilter == null || typeFilter == q.getType())
                .filter(q -> difficultyFilter == null || difficultyFilter.equals(q.getDifficulty()))
                .filter(q -> keywordFilter == null || matchesKeyword(q, keywordFilter))
                .filter(q -> statusFilter == null
                        || statusFilter.equals(statusLabel(progress.get(q.getId()))))
                .collect(Collectors.toCollection(ArrayList::new));

        // 默认排序:未做优先,其次错题,最后已做;同组内保持领域/难度/题号的原顺序
        matched.sort(Comparator.comparingInt(q -> statusRank(progress.get(q.getId()))));

        int pageSize = size <= 0 ? PAGE_SIZE_DEFAULT : Math.min(size, PAGE_SIZE_MAX);
        int pageNo = Math.max(page, 1);
        int from = Math.min((pageNo - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());

        List<QuestionBankView.Item> items = matched.subList(from, to).stream()
                .map(q -> toItem(q, progress.get(q.getId())))
                .toList();

        return new QuestionBankView(summary, matched.size(), pageNo, pageSize, items);
    }

    /** 做题统计:只返回 summary,前端刷新进度时用 */
    public QuestionBankView.Summary stats(long userId, String judgeMode) {
        JudgeMode modeFilter = normalizeJudgeMode(judgeMode);
        return summarize(scopeByMode(allQuestions(), modeFilter), loadProgress(userId));
    }

    /** 筛选项:领域与题型的动态列表,附带题目数量,避免前端硬编码 */
    public QuestionBankFacets facets(String judgeMode) {
        JudgeMode modeFilter = normalizeJudgeMode(judgeMode);
        List<QuestionBank> all = scopeByMode(allQuestions(), modeFilter);

        Map<String, Integer> domainCounts = new LinkedHashMap<>();
        for (QuestionBank question : all) {
            String domain = Texts.blankToNull(question.getDomain());
            domainCounts.merge(domain == null ? "其他" : domain, 1, Integer::sum);
        }
        List<QuestionBankFacets.Count> domains = domainCounts.entrySet().stream()
                .map(entry -> new QuestionBankFacets.Count(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(QuestionBankFacets.Count::count).reversed()
                        .thenComparing(QuestionBankFacets.Count::value))
                .toList();

        Map<QuestionType, Integer> typeCounts = new LinkedHashMap<>();
        for (QuestionType type : QuestionType.values()) {
            typeCounts.put(type, 0);
        }
        for (QuestionBank question : all) {
            QuestionType type = question.getType() == null ? QuestionType.BASICS : question.getType();
            typeCounts.merge(type, 1, Integer::sum);
        }
        List<QuestionBankFacets.Count> types = typeCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new QuestionBankFacets.Count(entry.getKey().name(), entry.getValue()))
                .toList();

        return new QuestionBankFacets(domains, types);
    }

    // ---------- 详情 ----------

    /** 题目详情;首次作答前隐藏参考答案和关键要点 */
    public QuestionBankDetail detail(long userId, long id) {
        QuestionBank question = requireOn(id);
        UserQuestionProgress progress = findProgress(userId, id);
        boolean attempted = progress != null && intValue(progress.getAttemptCount()) > 0;

        List<QuestionBankDetail.AttemptView> attempts = attempted
                ? attemptMapper.selectList(new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .eq("question_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + ATTEMPT_HISTORY_LIMIT))
                        .stream().map(this::toAttemptView).toList()
                : List.of();

        return new QuestionBankDetail(
                String.valueOf(question.getId()),
                seqOf(question),
                question.getTitle(),
                question.getContent(),
                enumName(question.getType()),
                question.getJudgeMode() == null ? JudgeMode.TEXT.name() : question.getJudgeMode().name(),
                intValue(question.getDifficulty()),
                question.getDomain(),
                names(question.getKpNames()),
                attempted ? names(question.getKeyPoints()) : List.of(),
                attempted ? question.getReferenceAnswer() : null,
                statusLabel(progress),
                intValue(progress == null ? null : progress.getAttemptCount()),
                progress == null ? null : progress.getBestScore(),
                progress == null ? null : progress.getLastScore(),
                progress == null ? null : format(progress.getLastAttemptAt()),
                attempts);
    }

    /** 详情页连续刷题:只在同一模式(问答题/编程题)内找前后相邻题 */
    public QuestionNeighbors neighbors(long id) {
        QuestionBank question = requireOn(id);
        List<QuestionBank> scoped = scopeByMode(allQuestions(), modeOf(question));
        int index = indexOf(scoped, id);
        String prevId = index > 0 ? String.valueOf(scoped.get(index - 1).getId()) : null;
        String nextId = index >= 0 && index + 1 < scoped.size()
                ? String.valueOf(scoped.get(index + 1).getId()) : null;
        return new QuestionNeighbors(prevId, nextId);
    }

    /** 随机一题,可排除当前题,并限定在问答题/编程题内 */
    public String randomQuestionId(Long excludeId, String judgeMode) {
        JudgeMode modeFilter = normalizeJudgeMode(judgeMode);
        List<QuestionBank> pool = scopeByMode(allQuestions(), modeFilter).stream()
                .filter(q -> excludeId == null || !excludeId.equals(q.getId()))
                .toList();
        if (pool.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "题库暂时为空");
        }
        return String.valueOf(pool.get(ThreadLocalRandom.current().nextInt(pool.size())).getId());
    }

    /**
     * 为面试手撕代码挑一道力扣模式题目。
     * 优先匹配难度,没有合适的就放宽难度;返回 null 表示当前题库没有可用编程题。
     */
    public QuestionBank pickCodingQuestion(int difficulty, Set<Long> excludeIds) {
        List<QuestionBank> pool = allQuestions().stream()
                .filter(q -> q.getJudgeMode() == JudgeMode.CODING)
                .filter(q -> q.getStarterCodeFunction() != null && !q.getStarterCodeFunction().isEmpty())
                .filter(q -> hasLeetCodeCases(q.getId()))
                .filter(q -> excludeIds == null || !excludeIds.contains(q.getId()))
                .toList();
        if (pool.isEmpty()) {
            return null;
        }
        List<QuestionBank> preferred = pool.stream()
                .filter(q -> q.getDifficulty() != null && Math.abs(q.getDifficulty() - difficulty) <= 1)
                .toList();
        List<QuestionBank> candidates = preferred.isEmpty() ? pool : preferred;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * FREE_RUN 面试选题:只要求是上架编程题且有题面,不需要测试用例和函数签名。
     */
    public QuestionBank pickFreeCodingQuestion(int difficulty, Set<Long> excludeIds) {
        // 优先使用面试专用题面:它直接给出具体输入和期望输出,不再出现 ACM 输入格式
        List<QuestionBank> pool = allQuestions().stream()
                .filter(q -> q.getJudgeMode() == JudgeMode.CODING)
                .filter(q -> q.getFreeContent() != null && !q.getFreeContent().isBlank())
                .filter(q -> excludeIds == null || !excludeIds.contains(q.getId()))
                .toList();
        if (pool.isEmpty()) {
            pool = allQuestions().stream()
                    .filter(q -> q.getJudgeMode() == JudgeMode.CODING)
                    .filter(q -> q.getContent() != null && !q.getContent().isBlank())
                    .filter(q -> excludeIds == null || !excludeIds.contains(q.getId()))
                    .toList();
        }
        if (pool.isEmpty()) {
            return null;
        }
        List<QuestionBank> preferred = pool.stream()
                .filter(q -> q.getDifficulty() != null && Math.abs(q.getDifficulty() - difficulty) <= 1)
                .toList();
        List<QuestionBank> candidates = preferred.isEmpty() ? pool : preferred;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean hasLeetCodeCases(Long questionId) {
        return testCaseMapper.selectCount(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", questionId)
                .eq("case_mode", "LEETCODE")) > 0;
    }

    /** 某道题的作答历史,最新在前 */
    public List<QuestionBankDetail.AttemptView> attempts(long userId, long id) {
        requireOn(id);
        return attemptMapper.selectList(new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .eq("question_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + ATTEMPT_HISTORY_LIMIT))
                .stream().map(this::toAttemptView).toList();
    }

    // ---------- 作答 ----------

    /** 提交作答:AI 评分 → 写作答与进度 → 回写掌握度与错题本 */
    public QuestionAttemptResult attempt(long userId, long id, String answer) {
        String text = answer == null ? "" : answer.strip();
        if (text.length() < MIN_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "再想想,至少写 " + MIN_ANSWER_LENGTH + " 个字");
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回答不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }

        QuestionBank question = requireOn(id);
        ReentrantLock lock = lockOf(userId, id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "这道题正在批改,请稍后再试");
        }
        try {
            // 慢调用放在事务外,避免长时间占用数据库连接
            BankEvaluation evaluation = llmService.bankEvaluate(question, text, userId);
            LocalDateTime now = LocalDateTime.now();

            QuestionAttemptResult result = transactionTemplate.execute(status -> {
                QuestionAttempt attempt = new QuestionAttempt();
                attempt.setId(idGenerator.nextId());
                attempt.setUserId(userId);
                attempt.setQuestionId(id);
                attempt.setAnswer(text);
                attempt.setScore(evaluation.score());
                attempt.setHitPoints(evaluation.hitPoints());
                attempt.setMissedPoints(evaluation.missedPoints());
                attempt.setComment(evaluation.comment());
                attempt.setSuggestion(evaluation.suggestion());
                attempt.setCreatedAt(now);
                attemptMapper.insert(attempt);

                UserQuestionProgress progress = upsertProgress(userId, id, evaluation.score(), now);

                return new QuestionAttemptResult(
                        String.valueOf(attempt.getId()),
                        evaluation.score(),
                        names(evaluation.hitPoints()),
                        names(evaluation.missedPoints()),
                        evaluation.comment(),
                        evaluation.suggestion(),
                        question.getReferenceAnswer(),
                        names(question.getKeyPoints()),
                        capabilityStatus(intValue(progress.getStatus())),
                        intValue(progress.getAttemptCount()),
                        progress.getBestScore(),
                        format(now));
            });

            return result;
        } finally {
            lock.unlock();
        }
    }

    // ---------- 编程题联动 ----------

    /** 取编程题;非编程题直接拒绝 */
    public QuestionBank requireCodingQuestion(long id) {
        QuestionBank question = requireOn(id);
        if (question.getJudgeMode() != JudgeMode.CODING) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这道题不是编程题");
        }
        return question;
    }

    /** 当前用户在这道题上的状态标签 TODO / DONE / WRONG */
    public String progressStatus(long userId, long id) {
        return statusLabel(findProgress(userId, id));
    }

    /** 判题结束后回写做题进度 */
    public void recordCodingOutcome(long userId, long id, boolean accepted, String code, String message) {
        requireOn(id);
        int score = accepted ? 5 : 1;
        transactionTemplate.executeWithoutResult(status -> upsertProgress(userId, id, score, LocalDateTime.now()));
    }

    private UserQuestionProgress upsertProgress(long userId, long id, int score, LocalDateTime now) {
        UserQuestionProgress progress = findProgress(userId, id);
        int status = score <= WRONG_SCORE_MAX ? WRONG : DONE;
        if (progress == null) {
            progress = new UserQuestionProgress();
            progress.setId(idGenerator.nextId());
            progress.setUserId(userId);
            progress.setQuestionId(id);
            progress.setStatus(status);
            progress.setAttemptCount(1);
            progress.setBestScore(score);
            progress.setLastScore(score);
            progress.setLastAttemptAt(now);
            try {
                progressMapper.insert(progress);
                return progress;
            } catch (DuplicateKeyException e) {
                // 极端并发:别人刚插入,转成更新
                progress = findProgress(userId, id);
                if (progress == null) {
                    throw e;
                }
            }
        }
        progress.setStatus(status);
        progress.setAttemptCount(intValue(progress.getAttemptCount()) + 1);
        progress.setBestScore(Math.max(intValue(progress.getBestScore()), score));
        progress.setLastScore(score);
        progress.setLastAttemptAt(now);
        progressMapper.updateById(progress);
        return progress;
    }

    // ---------- 导入 ----------

    /** 管理端批量导入;按题干 SHA-256 幂等,重复题跳过 */
    public QuestionBankImportResult importQuestions(List<QuestionBankImportRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            return new QuestionBankImportResult(0, 0);
        }
        int imported = 0;
        int skipped = 0;
        for (QuestionBankImportRequest.Item item : items) {
            if (item == null || Texts.blankToNull(item.content()) == null) {
                skipped++;
                continue;
            }
            String content = item.content().strip();
            String hash = sha256(content);
            Long exists = bankMapper.selectCount(new QueryWrapper<QuestionBank>()
                    .eq("content_hash", hash));
            if (exists != null && exists > 0) {
                skipped++;
                continue;
            }

            boolean coding = JudgeMode.CODING.name().equalsIgnoreCase(item.judgeMode());
            List<QuestionBankImportRequest.TestCaseItem> cases = coding
                    ? validCases(item.testCases()) : List.of();
            // 编程题没有用例就无法运行/判题,直接跳过,避免导入后是坏数据
            if (coding && cases.isEmpty()) {
                skipped++;
                continue;
            }

            QuestionBank question = new QuestionBank();
            question.setId(idGenerator.nextId());
            question.setTitle(clampTitle(Texts.blankToNull(item.title()) == null
                    ? preview(content, TITLE_MAX) : item.title().strip()));
            question.setContent(content);
            question.setDifficulty(item.difficulty() == null ? 2 : clamp(item.difficulty(), 1, 3));
            question.setDomain(Texts.blankToNull(item.domain()) == null ? "其他" : item.domain().strip());
            question.setReferenceAnswer(Texts.blankToNull(item.referenceAnswer()));
            question.setKeyPoints(limited(item.keyPoints(), 8, 200));
            List<String> kpNames = limited(item.kpNames(), 6, 64);
            question.setKpNames(kpNames.isEmpty() ? question.getKeyPoints() : kpNames);
            question.setKpIds(List.of());
            question.setSource(QuestionSource.from(item.source()));
            question.setSourceUrl(Texts.blankToNull(item.sourceUrl()));
            question.setContentHash(hash);
            question.setStatus(ONLINE);
            if (coding) {
                fillCoding(question, item, cases);
            } else {
                question.setType(QuestionType.from(item.type()));
                question.setJudgeMode(JudgeMode.TEXT);
            }

            // 题库里同一道编程题只保留一份:标题已存在时跳过,避免导入与历史种子重复
            if (coding) {
                Long sameTitle = bankMapper.selectCount(new QueryWrapper<QuestionBank>()
                        .eq("title", question.getTitle()));
                if (sameTitle != null && sameTitle > 0) {
                    skipped++;
                    continue;
                }
            }

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    bankMapper.insert(question);
                    for (QuestionBankImportRequest.TestCaseItem testCase : cases) {
                        testCaseMapper.insert(toEntity(question.getId(), testCase));
                    }
                });
                imported++;
            } catch (DuplicateKeyException e) {
                skipped++;
            }
        }
        return new QuestionBankImportResult(imported, skipped);
    }

    /** 编程题的判题配置:题型、模式、函数签名、初始代码、时限 */
    private static void fillCoding(QuestionBank question, QuestionBankImportRequest.Item item,
                                   List<QuestionBankImportRequest.TestCaseItem> cases) {
        question.setType(QuestionType.CODE);
        question.setJudgeMode(JudgeMode.CODING);
        String codeMode = Texts.blankToNull(item.codeMode());
        if (codeMode == null) {
            codeMode = cases.stream()
                    .anyMatch(testCase -> "LEETCODE".equalsIgnoreCase(testCase.caseMode()))
                    ? "LEETCODE" : "ACM";
        }
        question.setCodeMode(codeMode.strip().toUpperCase(Locale.ROOT));
        question.setFunctionName(Texts.blankToNull(item.functionName()));
        question.setParamTypes(limited(item.paramTypes(), 8, 32));
        question.setReturnType(Texts.blankToNull(item.returnType()));
        question.setStarterCode(item.starterCode() == null ? Map.of() : item.starterCode());
        question.setStarterCodeFunction(item.starterCodeFunction() == null
                ? Map.of() : item.starterCodeFunction());
        question.setTimeLimitMs(item.timeLimitMs() == null ? null : clamp(item.timeLimitMs(), 200, 20_000));
        question.setMemoryLimitMb(item.memoryLimitMb() == null ? null : clamp(item.memoryLimitMb(), 64, 1024));
    }

    /** 过滤掉没有期望输出的用例,并规范化模式、例题标记与排序 */
    private static List<QuestionBankImportRequest.TestCaseItem> validCases(
            List<QuestionBankImportRequest.TestCaseItem> cases) {
        List<QuestionBankImportRequest.TestCaseItem> result = new ArrayList<>();
        if (cases == null) {
            return result;
        }
        int index = 0;
        for (QuestionBankImportRequest.TestCaseItem testCase : cases) {
            index++;
            if (testCase == null || Texts.blankToNull(testCase.expectedOutput()) == null) {
                continue;
            }
            String caseMode = "LEETCODE".equalsIgnoreCase(testCase.caseMode()) ? "LEETCODE" : "ACM";
            int isSample = testCase.isSample() != null && testCase.isSample() == 1 ? 1 : 0;
            int sort = testCase.sort() == null ? index : testCase.sort();
            result.add(new QuestionBankImportRequest.TestCaseItem(caseMode, testCase.input(),
                    testCase.expectedOutput(), isSample, sort));
            if (result.size() >= 40) {
                break;
            }
        }
        return result;
    }

    private QuestionTestCase toEntity(long questionId, QuestionBankImportRequest.TestCaseItem item) {
        QuestionTestCase testCase = new QuestionTestCase();
        testCase.setId(idGenerator.nextId());
        testCase.setQuestionId(questionId);
        testCase.setCaseMode(item.caseMode());
        testCase.setInput(item.input());
        testCase.setExpectedOutput(item.expectedOutput());
        testCase.setIsSample(item.isSample());
        testCase.setSort(item.sort());
        return testCase;
    }

    // ---------- 规则与工具 ----------

    private List<QuestionBank> allQuestions() {
        return bankMapper.selectList(new QueryWrapper<QuestionBank>()
                .eq("status", ONLINE)
                .orderByAsc("domain")
                .orderByAsc("difficulty")
                .orderByAsc("id"));
    }

    private Map<Long, UserQuestionProgress> loadProgress(long userId) {
        Map<Long, UserQuestionProgress> map = new LinkedHashMap<>();
        for (UserQuestionProgress progress : progressMapper.selectList(
                new QueryWrapper<UserQuestionProgress>().eq("user_id", userId))) {
            map.put(progress.getQuestionId(), progress);
        }
        return map;
    }

    private UserQuestionProgress findProgress(long userId, long id) {
        return progressMapper.selectOne(new QueryWrapper<UserQuestionProgress>()
                .eq("user_id", userId)
                .eq("question_id", id)
                .last("LIMIT 1"));
    }

    private QuestionBankView.Summary summarize(List<QuestionBank> questions,
                                               Map<Long, UserQuestionProgress> progress) {
        int done = 0;
        int wrong = 0;
        int todo = 0;
        int totalAttempts = 0;
        for (QuestionBank question : questions) {
            UserQuestionProgress item = progress.get(question.getId());
            if (item == null || intValue(item.getAttemptCount()) == 0) {
                todo++;
                continue;
            }
            if (intValue(item.getStatus()) == WRONG) {
                wrong++;
            } else {
                done++;
            }
            totalAttempts += intValue(item.getAttemptCount());
        }
        return new QuestionBankView.Summary(questions.size(), done, wrong, todo, totalAttempts);
    }

    private QuestionBank requireOn(long id) {
        QuestionBank question = bankMapper.selectById(id);
        if (question == null || intValue(question.getStatus()) != ONLINE) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在或已下架");
        }
        return question;
    }

    private QuestionBankView.Item toItem(QuestionBank question, UserQuestionProgress progress) {
        return new QuestionBankView.Item(
                String.valueOf(question.getId()),
                question.getTitle(),
                preview(question.getContent(), MAX_PREVIEW_CHARS),
                enumName(question.getType()),
                question.getJudgeMode() == null ? JudgeMode.TEXT.name() : question.getJudgeMode().name(),
                question.getCodeMode(),
                intValue(question.getDifficulty()),
                question.getDomain(),
                names(question.getKpNames()),
                statusLabel(progress),
                intValue(progress == null ? null : progress.getAttemptCount()),
                progress == null ? null : progress.getBestScore(),
                progress == null ? null : progress.getLastScore(),
                progress == null ? null : format(progress.getLastAttemptAt()));
    }

    private QuestionBankDetail.AttemptView toAttemptView(QuestionAttempt attempt) {
        return new QuestionBankDetail.AttemptView(
                String.valueOf(attempt.getId()),
                attempt.getAnswer(),
                intValue(attempt.getScore()),
                names(attempt.getHitPoints()),
                names(attempt.getMissedPoints()),
                attempt.getComment(),
                attempt.getSuggestion(),
                format(attempt.getCreatedAt()));
    }

    /** 领域筛选:"其他"同时匹配空领域,避免 facets 里的"其他"点进去查不到题 */
    private static boolean matchesDomain(QuestionBank question, String filter) {
        String domain = Texts.blankToNull(question.getDomain());
        if ("其他".equals(filter)) {
            return domain == null || "其他".equals(domain);
        }
        return filter.equals(domain);
    }

    private static boolean matchesKeyword(QuestionBank question, String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(question.getTitle(), lower)
                || containsIgnoreCase(question.getContent(), lower);
    }

    private static boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    /** 默认排序权重:未做 0、错题 1、已做 2 */
    private static int statusRank(UserQuestionProgress progress) {
        String status = statusLabel(progress);
        if ("TODO".equals(status)) {
            return 0;
        }
        return "WRONG".equals(status) ? 1 : 2;
    }

    private static String statusLabel(UserQuestionProgress progress) {
        if (progress == null || intValue(progress.getAttemptCount()) == 0) {
            return "TODO";
        }
        return intValue(progress.getStatus()) == WRONG ? "WRONG" : "DONE";
    }

    private static String capabilityStatus(int status) {
        return status == WRONG ? "WRONG" : "DONE";
    }

    /** 题目在所属模式(问答题/编程题)默认排序中的序号,从 1 开始 */
    private int seqOf(QuestionBank question) {
        int index = indexOf(scopeByMode(allQuestions(), modeOf(question)), question.getId());
        return index < 0 ? 0 : index + 1;
    }

    private static JudgeMode modeOf(QuestionBank question) {
        return question.getJudgeMode() == null ? JudgeMode.TEXT : question.getJudgeMode();
    }

    private static List<QuestionBank> scopeByMode(List<QuestionBank> questions, JudgeMode mode) {
        if (mode == null) {
            return questions;
        }
        return questions.stream().filter(q -> modeOf(q) == mode).toList();
    }

    private static JudgeMode normalizeJudgeMode(String raw) {
        String value = Texts.blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return JudgeMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int indexOf(List<QuestionBank> questions, long id) {
        for (int i = 0; i < questions.size(); i++) {
            Long current = questions.get(i).getId();
            if (current != null && current == id) {
                return i;
            }
        }
        return -1;
    }

    private static QuestionType normalizeType(String type) {
        String value = Texts.blankToNull(type);
        if (value == null) {
            return null;
        }
        try {
            return QuestionType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeStatus(String status) {
        String value = Texts.blankToNull(status);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return STATUS_FILTERS.contains(upper) ? upper : null;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static List<String> names(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> limited(List<String> values, int max, int maxLength) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String text = Texts.blankToNull(value);
            if (text != null && result.size() < max) {
                result.add(text.length() <= maxLength ? text : text.substring(0, maxLength));
            }
        }
        return result;
    }

    /** 列表预览:去掉 Markdown 标记,避免每条都显示「## 题目描述」这类符号 */
    private static String preview(String content, int max) {
        if (content == null) {
            return "";
        }
        String text = content
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s*", "")
                .replaceAll("[*_`|~]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String clampTitle(String title) {
        return title.length() <= 200 ? title : title.substring(0, 200);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private ReentrantLock lockOf(long userId, long questionId) {
        return locks.computeIfAbsent(userId + ":" + questionId, key -> new ReentrantLock());
    }
}
