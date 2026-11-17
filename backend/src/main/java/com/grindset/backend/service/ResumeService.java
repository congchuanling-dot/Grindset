package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.CandidateType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.ParsedResume;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.InputLimits.MAX_RESUME_FILE_BYTES;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 简历业务:上传 → 抽文本 → LLM 结构化 → 落库;以及列表、详情、删除。
 * 解析是同步的,失败会把 parse_status 置为 2 并留下原因。
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final ResumeMapper resumeMapper;
    private final ResumeSectionMapper sectionMapper;
    private final ResumeSectionItemMapper itemMapper;
    private final ResumeSkillMapper skillMapper;
    private final ResumeTextExtractor extractor;
    private final ResumeLlmParser parser;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public ResumeService(ResumeMapper resumeMapper, ResumeSectionMapper sectionMapper,
                         ResumeSectionItemMapper itemMapper, ResumeSkillMapper skillMapper,
                         ResumeTextExtractor extractor, ResumeLlmParser parser,
                         SnowflakeIdGenerator idGenerator, TransactionTemplate transactionTemplate) {
        this.resumeMapper = resumeMapper;
        this.sectionMapper = sectionMapper;
        this.itemMapper = itemMapper;
        this.skillMapper = skillMapper;
        this.extractor = extractor;
        this.parser = parser;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    /** 上传并同步解析,返回完整详情 */
    public ResumeDetail upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的简历文件");
        }
        if (file.getSize() > MAX_RESUME_FILE_BYTES) {
            throw new BizException(413, "文件不能超过 10MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(422, "文件读取失败,请重新上传");
        }

        // 校验失败直接返回,不产生数据库记录
        ResumeTextExtractor.ExtractedText extracted = extractor.extract(file.getOriginalFilename(), bytes);

        long resumeId = idGenerator.nextId();
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setUserId(userId);
        resume.setFileName(extracted.fileName());
        resume.setFileType(extracted.fileType());
        resume.setFileSize(bytes.length);
        resume.setCandidateType(CandidateType.UNKNOWN.name());
        resume.setParseStatus(0);
        resumeMapper.insert(resume);

        ParsedResume parsed;
        String model;
        try {
            ResumeLlmParser.ParseResult result = parser.parse(extracted.text());
            parsed = result.resume();
            model = result.model();
        } catch (RuntimeException e) {
            markFailed(resumeId, e.getMessage());
            throw e;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> saveParsed(resumeId, parsed, model));
        } catch (RuntimeException e) {
            markFailed(resumeId, "解析结果保存失败");
            throw e;
        }
        return get(userId, resumeId);
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
                    row.getCandidateName(), row.getCandidateType(),
                    row.getParseStatus() == null ? 0 : row.getParseStatus(), format(row)));
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
                    section.getSectionType(), section.getTitle(), itemViews));
        }

        return new ResumeDetail(String.valueOf(resume.getId()), resume.getFileName(), resume.getFileType(),
                resume.getFileSize() == null ? 0 : resume.getFileSize(),
                resume.getParseStatus() == null ? 0 : resume.getParseStatus(),
                resume.getErrorMsg(), resume.getCandidateName(), resume.getCandidateType(),
                resume.getModel(), format(resume), skillViews, sectionViews);
    }

    /** 物理删除主表与所有子表数据 */
    public void delete(long userId, long id) {
        requireOwned(userId, id);
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
    }

    /** 事务内写入章节、条目、技能,并把主表更新为解析成功 */
    private void saveParsed(long resumeId, ParsedResume parsed, String model) {
        int sectionSort = 0;
        for (ParsedResume.Section section : parsed.getSections()) {
            ResumeSection sectionRow = new ResumeSection();
            sectionRow.setId(idGenerator.nextId());
            sectionRow.setResumeId(resumeId);
            sectionRow.setSectionType(section.getType());
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
        patch.setCandidateType(parsed.getCandidateType());
        patch.setModel(model);
        patch.setParseStatus(1);
        patch.setErrorMsg("");
        resumeMapper.updateById(patch);
    }

    /** 解析失败留痕:单独更新状态,不影响已提交的主表记录 */
    private void markFailed(long resumeId, String message) {
        String errorMsg = message == null || message.isBlank() ? "解析失败" : message.strip();
        if (errorMsg.length() > MAX_ERROR_LENGTH) {
            errorMsg = errorMsg.substring(0, MAX_ERROR_LENGTH);
        }
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setParseStatus(2);
        patch.setErrorMsg(errorMsg);
        try {
            resumeMapper.updateById(patch);
        } catch (RuntimeException e) {
            log.warn("更新简历失败状态出错 resumeId={}", resumeId, e);
        }
    }

    private Resume requireOwned(long userId, long id) {
        Resume resume = resumeMapper.selectOne(new QueryWrapper<Resume>()
                .eq("id", id)
                .eq("user_id", userId));
        if (resume == null) {
            throw new BizException(404, "简历不存在");
        }
        return resume;
    }

    private static String format(Resume resume) {
        return resume.getCreatedAt() == null ? "" : resume.getCreatedAt().format(STANDARD);
    }
}
