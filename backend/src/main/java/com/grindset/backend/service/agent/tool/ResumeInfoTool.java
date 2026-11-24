package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.ResumeSummary;
import com.grindset.backend.service.ResumeService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import com.grindset.backend.service.interview.InterviewContextBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.ResumeParseStatus.FAILED;
import static com.grindset.backend.common.constant.ResumeParseStatus.SUCCESS;

/**
 * 当前用户自己的简历内容。
 * 不需要前端传用户 ID:AgentService 从 JWT 取登录用户后直接传给 execute。
 *
 * <p>一个用户可能有多份简历,所以无参调用时:只有一份解析成功就直接返回正文,
 * 多份则返回选择卡片由用户点选,再带 resumeId / 文件名关键字调用一次。</p>
 */
@Component
public class ResumeInfoTool implements AgentTool {

    private final ResumeService resumeService;
    private final InterviewContextBuilder contextBuilder;

    public ResumeInfoTool(ResumeService resumeService, InterviewContextBuilder contextBuilder) {
        this.resumeService = resumeService;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public String name() {
        return "get_my_resume";
    }

    @Override
    public String label() {
        return "查看我的简历";
    }

    @Override
    public String description() {
        return "读取当前用户自己的简历,返回技能清单和经历章节的纯文本。"
                + "可选参数:resumeId(精确指定)或 fileName(文件名关键字,模糊匹配)。"
                + "都不传时,只有一份解析成功的简历就直接返回;有多份则返回简历清单,等用户选定后再带参数调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "resumeId", Map.of("type", "string",
                                "description", "简历 ID,从简历清单里取,精确匹配"),
                        "fileName", Map.of("type", "string",
                                "description", "简历文件名关键字,模糊匹配")),
                "required", List.of());
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        List<ResumeSummary> resumes = resumeService.list(userId);
        if (resumes.isEmpty()) {
            return new AgentToolResult(true, "还没有上传简历",
                    Map.of("message", "当前用户还没有上传任何简历"));
        }

        String resumeId = AgentToolArgs.string(args, "resumeId");
        if (resumeId != null) {
            ResumeSummary target = resumes.stream()
                    .filter(item -> item.id().equals(resumeId))
                    .findFirst()
                    .orElse(null);
            return target == null
                    ? new AgentToolResult(false, "没有找到这份简历",
                    Map.of("message", "当前用户名下没有 ID 为 " + resumeId + " 的简历,请重新查看清单"))
                    : contentOrStatus(userId, target);
        }

        String fileName = AgentToolArgs.string(args, "fileName");
        if (fileName != null) {
            String keyword = fileName.toLowerCase();
            List<ResumeSummary> matched = resumes.stream()
                    .filter(item -> item.fileName() != null
                            && item.fileName().toLowerCase().contains(keyword))
                    .toList();
            if (matched.isEmpty()) {
                return new AgentToolResult(false, "没有匹配到简历",
                        Map.of("message", "没有文件名包含「" + fileName + "」的简历,请重新查看清单"));
            }
            List<ResumeSummary> usable = matched.stream()
                    .filter(item -> item.parseStatus() == SUCCESS)
                    .toList();
            if (usable.size() == 1) {
                return contentOrStatus(userId, usable.get(0));
            }
            return usable.size() > 1
                    ? selector(usable)
                    : new AgentToolResult(true, "匹配到的简历还没有解析完成",
                    Map.of("message", "文件名包含「" + fileName + "」的简历还在解析中或解析失败"));
        }

        // 无参:列表已按创建时间倒序
        List<ResumeSummary> usable = resumes.stream()
                .filter(item -> item.parseStatus() == SUCCESS)
                .toList();
        if (usable.size() == 1) {
            return contentOrStatus(userId, usable.get(0));
        }
        if (usable.size() > 1) {
            return selector(usable);
        }
        return contentOrStatus(userId, resumes.get(0));
    }

    /** 解析成功返回正文,否则返回可读的状态提示 */
    private AgentToolResult contentOrStatus(long userId, ResumeSummary resume) {
        if (resume.parseStatus() != SUCCESS) {
            String message = resume.parseStatus() == FAILED
                    ? "简历「" + resume.fileName() + "」解析失败,需要重新上传"
                    : "简历「" + resume.fileName() + "」还在解析中,请稍后再查";
            return new AgentToolResult(true, message, Map.of("message", message));
        }

        // 与面试出题/评分共用同一份文本格式,保证模型看到的内容一致
        String content = contextBuilder.resumeText(userId, Long.parseLong(resume.id()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resumeId", resume.id());
        data.put("fileName", resume.fileName());
        data.put("candidateName", resume.candidateName() == null ? "" : resume.candidateName());
        data.put("content", content);
        return new AgentToolResult(true, "已读取简历「" + resume.fileName() + "」", data);
    }

    /** 多份时返回选择卡片,前端点击后把 prompt 当作用户提问发回来,气泡只展示 display */
    private AgentToolResult selector(List<ResumeSummary> resumes) {
        List<Map<String, Object>> items = new ArrayList<>(resumes.size());
        for (ResumeSummary resume : resumes) {
            String display = "查看简历「" + resume.fileName() + "」";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", resume.id());
            item.put("title", resume.fileName());
            item.put("subtitle", subtitle(resume));
            item.put("display", display);
            item.put("prompt", display + "（resumeId=" + resume.id() + "）");
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("card", "selector");
        data.put("title", "想看哪一份?");
        data.put("items", items);
        return new AgentToolResult(true, "共 " + resumes.size() + " 份简历", data);
    }

    private static String subtitle(ResumeSummary resume) {
        String type = candidateTypeLabel(resume.candidateType());
        String createdAt = resume.createdAt();
        return createdAt == null || createdAt.isBlank() ? type : type + " · " + createdAt;
    }

    private static String candidateTypeLabel(String type) {
        if ("FRESH_GRADUATE".equals(type)) {
            return "应届生";
        }
        if ("INTERN".equals(type)) {
            return "找实习";
        }
        if ("EXPERIENCED".equals(type)) {
            return "社招";
        }
        return "未识别";
    }
}
