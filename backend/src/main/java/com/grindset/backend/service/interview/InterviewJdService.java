package com.grindset.backend.service.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.JobLevel;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.JdProfile;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.service.InterviewLlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JD 定制的入口:清洗输入、调用模型解析岗位画像、读写会话里的画像 JSON。
 * 解析失败不阻断创建,降级为"只参考 JD 原文"。
 */
@Service
public class InterviewJdService {

    private static final Logger log = LoggerFactory.getLogger(InterviewJdService.class);

    public static final int MAX_JD_LENGTH = 8000;

    private final InterviewLlmService llmService;
    private final InterviewContextBuilder contextBuilder;
    private final ObjectMapper objectMapper;

    public InterviewJdService(InterviewLlmService llmService,
                              InterviewContextBuilder contextBuilder,
                              ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.contextBuilder = contextBuilder;
        this.objectMapper = objectMapper;
    }

    /** 空串归一为 null,超长截断;前端字数限制之外再兜一层 */
    public String normalizeJdText(String raw) {
        String value = Texts.blankToNull(raw);
        return value == null ? null : Texts.truncate(value, MAX_JD_LENGTH);
    }

    public String normalizeCompany(String raw) {
        String value = Texts.blankToNull(raw);
        return value == null ? null : Texts.truncate(value, 64);
    }

    /** 解析 JD;失败返回 null,由调用方按"未解析"处理 */
    public JdProfile parseProfile(long userId, long resumeId, String position, String jdText,
                                  String company, JobLevel jobLevel) {
        if (jdText == null || jdText.isBlank()) {
            return null;
        }
        try {
            String resumeText = contextBuilder.resumeText(userId, resumeId);
            JdProfile profile = llmService.parseJdProfile(jdText, resumeText, company, jobLevel, userId);
            return applyDefaults(profile, position, company, jobLevel);
        } catch (RuntimeException e) {
            log.warn("JD 解析失败,降级为原文参考,resumeId={}, reason={}", resumeId, e.getMessage());
            return null;
        }
    }

    /** 前端预览后回传的画像:只做清洗,不再调模型;不合法时返回 null 让调用方重新解析 */
    public JdProfile normalizeProvided(JdProfile profile, String position, String company, JobLevel jobLevel) {
        if (profile == null) {
            return null;
        }
        try {
            return applyDefaults(JdProfileNormalizer.normalize(profile), position, company, jobLevel);
        } catch (RuntimeException e) {
            log.warn("预览回传的岗位画像不合法,将重新解析,reason={}", e.getMessage());
            return null;
        }
    }

    /** 从会话读取画像;JSON 损坏时返回 null,不影响面试流程 */
    public JdProfile profileOf(InterviewSession session) {
        if (session == null || session.getJdProfileJson() == null
                || session.getJdProfileJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(session.getJdProfileJson(), JdProfile.class);
        } catch (JsonProcessingException e) {
            log.warn("岗位画像 JSON 解析失败,sessionId={}", session.getId());
            return null;
        }
    }

    public String toJson(JdProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            log.warn("岗位画像序列化失败", e);
            return null;
        }
    }

    private static JdProfile applyDefaults(JdProfile profile, String position,
                                           String company, JobLevel jobLevel) {
        if (profile == null) {
            return null;
        }
        String title = Texts.blankToNull(profile.title());
        String profileCompany = Texts.blankToNull(profile.company());
        String level = Texts.blankToNull(profile.level());
        return new JdProfile(
                title == null ? position : title,
                profileCompany == null ? company : profileCompany,
                level == null ? (jobLevel == null ? null : jobLevel.label()) : level,
                profile.mustHave(),
                profile.niceToHave(),
                profile.responsibilities(),
                profile.competencies(),
                profile.resumeGaps());
    }
}
