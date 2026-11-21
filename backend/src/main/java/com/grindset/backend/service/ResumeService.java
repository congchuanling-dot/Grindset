package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.CandidateType;
import com.grindset.backend.common.ResumeFileType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.dto.ResumeSummary;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.entity.ResumeSection;
import com.grindset.backend.entity.ResumeSectionItem;
import com.grindset.backend.entity.ResumeSkill;
import com.grindset.backend.mapper.ResumeMapper;
import com.grindset.backend.mapper.ResumeSectionItemMapper;
import com.grindset.backend.mapper.ResumeSectionMapper;
import com.grindset.backend.mapper.ResumeSkillMapper;
import com.grindset.backend.service.resume.ResumeParsePublisher;
import com.grindset.backend.service.resume.ResumeParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static com.grindset.backend.common.constant.InputLimits.MAX_RESUME_FILE_BYTES;
import static com.grindset.backend.common.constant.ResumeParseStatus.PARSING;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 简历业务:上传 → 抽文本 → LLM 结构化 → 落库;以及列表、详情、删除。
 * 解析是同步的,失败会把 parse_status 置为 2 并留下原因。
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeMapper resumeMapper;
    private final ResumeSectionMapper sectionMapper;
    private final ResumeSectionItemMapper itemMapper;
    private final ResumeSkillMapper skillMapper;
    private final ResumeTextExtractor extractor;
    private final ResumeOriginalStorage originalStorage;
    private final ResumeParseService parseService;
    private final ResumeParsePublisher parsePublisher;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 单实例上传并发闸门,避免大量 PDF/OSS 同时占用内存 */
    private final Semaphore uploadSlots;

    public ResumeService(ResumeMapper resumeMapper, ResumeSectionMapper sectionMapper,
                         ResumeSectionItemMapper itemMapper, ResumeSkillMapper skillMapper,
                         ResumeTextExtractor extractor, ResumeOriginalStorage originalStorage,
                         ResumeParseService parseService, ResumeParsePublisher parsePublisher,
                         SnowflakeIdGenerator idGenerator, TransactionTemplate transactionTemplate,
                         @Value("${app.resume.parse.upload-permits:8}") int uploadPermits) {
        this.resumeMapper = resumeMapper;
        this.sectionMapper = sectionMapper;
        this.itemMapper = itemMapper;
        this.skillMapper = skillMapper;
        this.extractor = extractor;
        this.originalStorage = originalStorage;
        this.parseService = parseService;
        this.parsePublisher = parsePublisher;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.uploadSlots = new Semaphore(Math.max(1, uploadPermits));
    }

    /** 上传并异步解析:落库 + 投递 MQ + 立即返回;OSS 不可用时回退同步解析 */
    public ResumeDetail upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择要上传的简历文件");
        }
        if (file.getSize() > MAX_RESUME_FILE_BYTES) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE, "文件不能超过 10MB");
        }
        //todo:之后改成等待而不是直接抛异常
        if (!uploadSlots.tryAcquire()) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "当前上传人数较多，请稍后重试");
        }

        Path temp = null;
        try {
            // 流式落盘,堆里不保留整份文件
            temp = Files.createTempFile("grindset-resume-upload-", ".upload");
            file.transferTo(temp.toFile());
            long fileSize = Files.size(temp);
            ResumeTextExtractor.Preflight preflight = extractor.preflight(file.getOriginalFilename(), temp);

            long resumeId = idGenerator.nextId();
            String objectKey = originalStorage.upload(userId, resumeId, preflight.fileType(), temp, fileSize);

            Resume resume = new Resume();
            resume.setId(resumeId);
            resume.setUserId(userId);
            resume.setFileName(preflight.fileName());
            resume.setFileType(preflight.fileType());
            resume.setFileSize((int) Math.min(fileSize, Integer.MAX_VALUE));
            resume.setOriginalObjectKey(objectKey);
            resume.setCandidateType(CandidateType.UNKNOWN);
            resume.setParseStatus(PARSING);
            resume.setParseAttempt(0);
            try {
                resumeMapper.insert(resume);
            } catch (RuntimeException e) {
                // 主表没落成,清理刚上传的对象,避免孤儿文件
                originalStorage.deleteQuietly(objectKey);
                throw e;
            }

            if (objectKey == null) {
                // OSS 未配置或上传失败:保持原来的同步解析行为
                parseSync(resume, temp, preflight);
            } else {
                try {
                    parsePublisher.publish(resume);
                } catch (RuntimeException e) {
                    log.warn("投递解析消息失败,回退同步解析 resumeId={}", resumeId, e);
                    parseSync(resume, temp, preflight);
                }
            }
            return get(userId, resumeId);
        } catch (IOException | IllegalStateException e) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "文件读取失败,请重新上传");
        } finally {
            deleteQuietly(temp);
            uploadSlots.release();
        }
    }

    private void parseSync(Resume resume, Path temp, ResumeTextExtractor.Preflight preflight) {
        try {
            parseService.parseFile(resume, temp, preflight.fileName());
        } catch (RuntimeException e) {
            parseService.markFailed(resume.getId(), e.getMessage());
            throw e;
        }
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

    /** 当前用户的简历列表,新的在前 */
    public List<ResumeSummary> list(long userId) {
        List<Resume> rows = resumeMapper.selectList(new QueryWrapper<Resume>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .orderByDesc("id"));
        List<ResumeSummary> result = new ArrayList<>(rows.size());
        for (Resume row : rows) {
            result.add(new ResumeSummary(String.valueOf(row.getId()), row.getFileName(),
                    row.getCandidateName(), enumName(row.getCandidateType()),
                    row.getParseStatus() == null ? PARSING : row.getParseStatus(), format(row)));
        }
        return result;
    }

    /** 详情:不存在或不属于当前用户都返回 404 */
    public ResumeDetail get(long userId, long id) {
        Resume resume = requireOwned(userId, id);

        List<ResumeSkill> skills = skillMapper.selectList(new QueryWrapper<ResumeSkill>()
                .eq("resume_id", id)
                .orderByAsc("sort")
                .orderByAsc("id"));
        List<ResumeSection> sections = sectionMapper.selectList(new QueryWrapper<ResumeSection>()
                .eq("resume_id", id)
                .orderByAsc("sort")
                .orderByAsc("id"));

        Map<Long, List<ResumeSectionItem>> itemsBySection = new LinkedHashMap<>();
        if (!sections.isEmpty()) {
            List<Long> sectionIds = sections.stream().map(ResumeSection::getId).toList();
            List<ResumeSectionItem> items = itemMapper.selectList(new QueryWrapper<ResumeSectionItem>()
                    .in("section_id", sectionIds)
                    .orderByAsc("sort")
                    .orderByAsc("id"));
            for (ResumeSectionItem item : items) {
                itemsBySection.computeIfAbsent(item.getSectionId(), key -> new ArrayList<>()).add(item);
            }
        }

        List<ResumeDetail.SkillView> skillViews = skills.stream()
                .map(skill -> new ResumeDetail.SkillView(String.valueOf(skill.getId()), skill.getName(),
                        skill.getCategory(), skill.getProficiency(), skill.getEvidence()))
                .toList();

        List<ResumeDetail.SectionView> sectionViews = new ArrayList<>(sections.size());
        for (ResumeSection section : sections) {
            List<ResumeDetail.ItemView> itemViews = itemsBySection
                    .getOrDefault(section.getId(), List.of()).stream()
                    .map(item -> new ResumeDetail.ItemView(String.valueOf(item.getId()), item.getTitle(),
                            item.getSubtitle(), item.getStartDate(), item.getEndDate(),
                            item.getDescription(), item.getTags() == null ? List.of() : item.getTags()))
                    .toList();
            sectionViews.add(new ResumeDetail.SectionView(String.valueOf(section.getId()),
                    enumName(section.getSectionType()), section.getTitle(), itemViews));
        }

        String objectKey = resume.getOriginalObjectKey();
        boolean hasOriginal = objectKey != null && !objectKey.isBlank();
        String originalUrl = hasOriginal ? originalStorage.previewUrl(objectKey) : null;
        String originalDownloadUrl = hasOriginal
                ? originalStorage.downloadUrl(objectKey, resume.getFileName()) : null;

        return new ResumeDetail(String.valueOf(resume.getId()), resume.getFileName(),
                enumName(resume.getFileType()),
                resume.getFileSize() == null ? 0 : resume.getFileSize(),
                hasOriginal, originalUrl, originalDownloadUrl,
                resume.getParseStatus() == null ? PARSING : resume.getParseStatus(),
                resume.getErrorMsg(), resume.getCandidateName(), enumName(resume.getCandidateType()),
                resume.getModel(), format(resume), skillViews, sectionViews);
    }

    /** 打开原件流,供 Controller 代理预览;原件不存在或存储未配置返回 404 */
    public OriginalFile openOriginal(long userId, long id) {
        Resume resume = requireOwned(userId, id);
        String objectKey = resume.getOriginalObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "原件未存储");
        }
        InputStream stream = originalStorage.open(objectKey);
        if (stream == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "原件不存在或存储未配置");
        }
        return new OriginalFile(stream, resume.getFileName(), resume.getFileType(),
                resume.getFileSize() == null ? 0 : resume.getFileSize());
    }

    /** 物理删除主表与所有子表数据,提交后再清理 OSS 原件 */
    public void delete(long userId, long id) {
        Resume resume = requireOwned(userId, id);
        String objectKey = resume.getOriginalObjectKey();
        transactionTemplate.executeWithoutResult(status -> {
            skillMapper.delete(new QueryWrapper<ResumeSkill>().eq("resume_id", id));

            List<ResumeSection> sections = sectionMapper.selectList(
                    new QueryWrapper<ResumeSection>().eq("resume_id", id));
            if (!sections.isEmpty()) {
                List<Long> sectionIds = sections.stream().map(ResumeSection::getId).toList();
                itemMapper.delete(new QueryWrapper<ResumeSectionItem>().in("section_id", sectionIds));
            }

            sectionMapper.delete(new QueryWrapper<ResumeSection>().eq("resume_id", id));
            resumeMapper.deleteById(id);
        });
        originalStorage.deleteQuietly(objectKey);
    }

    private Resume requireOwned(long userId, long id) {
        Resume resume = resumeMapper.selectOne(new QueryWrapper<Resume>()
                .eq("id", id)
                .eq("user_id", userId));
        if (resume == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "简历不存在");
        }
        return resume;
    }

    private static String format(Resume resume) {
        return resume.getCreatedAt() == null ? "" : resume.getCreatedAt().format(STANDARD);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * 原件流与展示信息,调用方负责关闭 stream。
     */
    public record OriginalFile(InputStream stream, String fileName, ResumeFileType fileType, long size) {
    }
}
