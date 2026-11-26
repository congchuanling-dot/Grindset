package com.grindset.backend.service.resume;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.CandidateType;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.ResumeFileType;
import com.grindset.backend.common.ResumeSectionType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.ParsedResume;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.entity.ResumeSection;
import com.grindset.backend.entity.ResumeSectionItem;
import com.grindset.backend.entity.ResumeSkill;
import com.grindset.backend.mapper.ResumeMapper;
import com.grindset.backend.mapper.ResumeSectionItemMapper;
import com.grindset.backend.mapper.ResumeSectionMapper;
import com.grindset.backend.mapper.ResumeSkillMapper;
import com.grindset.backend.service.ResumeLlmParser;
import com.grindset.backend.service.ResumeOriginalStorage;
import com.grindset.backend.service.ResumeTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static com.grindset.backend.common.constant.ResumeParseStatus.FAILED;
import static com.grindset.backend.common.constant.ResumeParseStatus.PARSING;
import static com.grindset.backend.common.constant.ResumeParseStatus.SUCCESS;

/**
 * 简历解析执行器:上传兜底同步解析和 RabbitMQ worker 共用。
 * 负责下载/抽取/LLM/事务落库,以及解析状态的推进。
 */
@Service
public class ResumeParseService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseService.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final ResumeMapper resumeMapper;
    private final ResumeSectionMapper sectionMapper;
    private final ResumeSectionItemMapper itemMapper;
    private final ResumeSkillMapper skillMapper;
    private final ResumeTextExtractor extractor;
    private final ResumeLlmParser parser;
    private final ResumeOriginalStorage originalStorage;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public ResumeParseService(ResumeMapper resumeMapper, ResumeSectionMapper sectionMapper,
                              ResumeSectionItemMapper itemMapper, ResumeSkillMapper skillMapper,
                              ResumeTextExtractor extractor, ResumeLlmParser parser,
                              ResumeOriginalStorage originalStorage,
                              SnowflakeIdGenerator idGenerator, TransactionTemplate transactionTemplate) {
        this.resumeMapper = resumeMapper;
        this.sectionMapper = sectionMapper;
        this.itemMapper = itemMapper;
        this.skillMapper = skillMapper;
        this.extractor = extractor;
        this.parser = parser;
        this.originalStorage = originalStorage;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    public Resume find(long resumeId) {
        return resumeMapper.selectById(resumeId);
    }

    /**
     * 从 OSS 原件执行一次异步解析。
     * 返回结果由 Consumer 决定 ack / 重试 / 死信。
     */
    public ParseOutcome parse(Resume resume, int attempt) {
        markParsing(resume.getId(), attempt);
        String objectKey = resume.getOriginalObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            String message = "原件未存储，无法异步解析";
            markFailed(resume.getId(), message);
            return ParseOutcome.permanent(message);
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("grindset-resume-parse-", ".upload");
            if (!originalStorage.downloadTo(objectKey, temp)) {
                // OSS 临时不可用或对象暂时读不到,交给重试队列;超过次数再进死信
                String message = "原件下载失败，稍后重试";
                markRetryableFailure(resume.getId(), attempt, message);
                return ParseOutcome.retryable(message);
            }
            parseFile(resume, temp, resume.getFileName());
            return ParseOutcome.ok();
        } catch (BizException e) {
            if (permanent(e)) {
                markFailed(resume.getId(), e.getMessage());
                return ParseOutcome.permanent(e.getMessage());
            }
            markRetryableFailure(resume.getId(), attempt, e.getMessage());
            return ParseOutcome.retryable(e.getMessage());
        } catch (Exception e) {
            log.warn("简历异步解析失败 resumeId={} attempt={}", resume.getId(), attempt, e);
            markRetryableFailure(resume.getId(), attempt, "解析失败，稍后重试");
            return ParseOutcome.retryable("解析失败，稍后重试");
        } finally {
            deleteQuietly(temp);
        }
    }

    /** 上传兜底:OSS 不可用时直接从临时文件同步解析 */
    public void parseFile(Resume resume, Path file, String fileName) {
        ResumeTextExtractor.ExtractedText extracted = extractor.extract(fileName, file);
        long userId = resume.getUserId() == null ? 0L : resume.getUserId();
        ResumeLlmParser.ParseResult result = parser.parse(extracted.text(), userId, resume.getId());
        saveParsed(resume.getId(), result.resume(), result.model(), false);
    }

    /**
     * 用前端编辑后的结构化内容直接覆盖数据库,不调 LLM。
     * 先规范化(枚举降级、长度截断、熟练度夹取),再在事务里整体替换章节/条目/技能。
     */
    public void replaceStructured(Resume resume, ParsedResume parsed) {
        if ((parsed.getSections() == null || parsed.getSections().isEmpty())
                && (parsed.getSkills() == null || parsed.getSkills().isEmpty())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历内容不能为空");
        }
        // 类型不在编辑范围内,沿用原来解析出的值
        parsed.setCandidateType(resume.getCandidateType() == null
                ? CandidateType.UNKNOWN.name() : resume.getCandidateType().name());
        saveParsed(resume.getId(), parser.normalize(parsed), resume.getModel(), true);
    }

    public void markParsing(long resumeId, int attempt) {
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setParseStatus(PARSING);
        patch.setParseAttempt(attempt);
        patch.setParseStartedAt(LocalDateTime.now());
        patch.setErrorMsg("");
        resumeMapper.updateById(patch);
    }

    public void markRetryableFailure(long resumeId, int attempt, String message) {
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setParseStatus(PARSING);
        patch.setParseAttempt(attempt);
        patch.setErrorMsg(truncate(message));
        resumeMapper.updateById(patch);
    }

    /** 永久失败:状态置 2,保留原因 */
    public void markFailed(long resumeId, String message) {
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setParseStatus(FAILED);
        patch.setParseFinishedAt(LocalDateTime.now());
        patch.setErrorMsg(truncate(message));
        try {
            resumeMapper.updateById(patch);
        } catch (RuntimeException e) {
            log.warn("更新简历失败状态出错 resumeId={}", resumeId, e);
        }
    }

    /** 事务内写入章节、条目、技能,并把主表更新为解析成功;replace=true 时先清空旧的结构化数据 */
    private void saveParsed(long resumeId, ParsedResume parsed, String model, boolean replace) {
        transactionTemplate.executeWithoutResult(status -> {
            if (replace) {
                skillMapper.delete(new QueryWrapper<ResumeSkill>().eq("resume_id", resumeId));
                List<ResumeSection> oldSections = sectionMapper.selectList(
                        new QueryWrapper<ResumeSection>().eq("resume_id", resumeId));
                if (!oldSections.isEmpty()) {
                    List<Long> oldSectionIds = oldSections.stream().map(ResumeSection::getId).toList();
                    itemMapper.delete(new QueryWrapper<ResumeSectionItem>().in("section_id", oldSectionIds));
                }
                sectionMapper.delete(new QueryWrapper<ResumeSection>().eq("resume_id", resumeId));
            }

            int sectionSort = 0;
            for (ParsedResume.Section section : parsed.getSections()) {
                ResumeSection sectionRow = new ResumeSection();
                sectionRow.setId(idGenerator.nextId());
                sectionRow.setResumeId(resumeId);
                sectionRow.setSectionType(ResumeSectionType.from(section.getType()));
                sectionRow.setTitle(section.getTitle());
                sectionRow.setSort(sectionSort++);
                sectionMapper.insert(sectionRow);

                int itemSort = 0;
                for (ParsedResume.Item item : section.getItems()) {
                    ResumeSectionItem itemRow = new ResumeSectionItem();
                    itemRow.setId(idGenerator.nextId());
                    itemRow.setSectionId(sectionRow.getId());
                    itemRow.setTitle(item.getTitle());
                    itemRow.setSubtitle(item.getSubtitle());
                    itemRow.setStartDate(item.getStartDate());
                    itemRow.setEndDate(item.getEndDate());
                    itemRow.setDescription(item.getDescription());
                    itemRow.setTags(item.getTags());
                    itemRow.setSort(itemSort++);
                    itemMapper.insert(itemRow);
                }
            }

            int skillSort = 0;
            for (ParsedResume.Skill skill : parsed.getSkills()) {
                ResumeSkill skillRow = new ResumeSkill();
                skillRow.setId(idGenerator.nextId());
                skillRow.setResumeId(resumeId);
                skillRow.setName(skill.getName());
                skillRow.setCategory(skill.getCategory());
                skillRow.setProficiency(skill.getProficiency());
                skillRow.setEvidence(skill.getEvidence());
                skillRow.setSort(skillSort++);
                skillMapper.insert(skillRow);
            }

            Resume patch = new Resume();
            patch.setId(resumeId);
            patch.setCandidateName(parsed.getCandidateName());
            patch.setCandidateType(CandidateType.from(parsed.getCandidateType()));
            patch.setModel(model);
            patch.setParseStatus(SUCCESS);
            patch.setParseFinishedAt(LocalDateTime.now());
            patch.setErrorMsg("");
            int updated = resumeMapper.updateById(patch);
            if (updated == 0) {
                // 解析期间简历被删除:抛异常让事务回滚,避免留下孤儿子表数据
                throw new BizException(ErrorCode.NOT_FOUND, "简历已删除,停止解析");
            }
        });
    }

    private static boolean permanent(BizException e) {
        int code = e.getCode();
        return code == ErrorCode.BAD_REQUEST || code == ErrorCode.NOT_FOUND
                || code == ErrorCode.PAYLOAD_TOO_LARGE || code == ErrorCode.UNPROCESSABLE_ENTITY;
    }

    private static String truncate(String message) {
        String value = message == null || message.isBlank() ? "解析失败" : message.strip();
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除简历临时文件失败 path={}", path, e);
        }
    }

    /**
     * 一次解析执行结果。
     */
    public record ParseOutcome(boolean success, boolean retryable, String errorMessage) {

        public static ParseOutcome ok() {
            return new ParseOutcome(true, false, null);
        }

        public static ParseOutcome retryable(String message) {
            return new ParseOutcome(false, true, message);
        }

        public static ParseOutcome permanent(String message) {
            return new ParseOutcome(false, false, message);
        }
    }
}
