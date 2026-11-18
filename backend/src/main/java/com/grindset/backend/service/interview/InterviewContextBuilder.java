package com.grindset.backend.service.interview;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.service.ResumeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 把结构化简历渲染成给模型看的纯文本。出题、答题评分、复盘共用同一份格式。
 */
@Component
public class InterviewContextBuilder {

    private final ResumeService resumeService;

    public InterviewContextBuilder(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** 简历全量结构化内容,不截断 */
    public String resumeText(long userId, long resumeId) {
        ResumeDetail resume;
        try {
            resume = resumeService.get(userId, resumeId);
        } catch (BizException e) {
            throw new BizException(ErrorCode.CONFLICT, "使用的简历已被删除,无法继续面试");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("姓名:").append(resume.candidateName() == null ? "未识别" : resume.candidateName())
                .append('\n');
        builder.append("候选人类型:").append(candidateTypeLabel(resume.candidateType())).append('\n');

        builder.append("技能清单:\n");
        if (resume.skills().isEmpty()) {
            builder.append("- 无\n");
        }
        for (ResumeDetail.SkillView skill : resume.skills()) {
            builder.append("- ").append(skill.name());
            List<String> meta = new ArrayList<>();
            if (skill.category() != null && !skill.category().isBlank()) {
                meta.add(skill.category());
            }
            if (skill.proficiency() != null) {
                meta.add(skill.proficiency() + "/5");
            }
            if (!meta.isEmpty()) {
                builder.append("(").append(String.join("、", meta)).append(")");
            }
            if (skill.evidence() != null && !skill.evidence().isBlank()) {
                builder.append(":").append(skill.evidence());
            }
            builder.append('\n');
        }

        builder.append("\n经历与章节:\n");
        for (ResumeDetail.SectionView section : resume.sections()) {
            builder.append("## ").append(section.title()).append('\n');
            if (section.items().isEmpty()) {
                builder.append("- 无条目\n");
            }
            for (ResumeDetail.ItemView item : section.items()) {
                builder.append("- ");
                builder.append(item.title() == null ? "未命名" : item.title());
                if (item.subtitle() != null && !item.subtitle().isBlank()) {
                    builder.append(" · ").append(item.subtitle());
                }
                String time = timeRange(item.startDate(), item.endDate());
                if (!time.isEmpty()) {
                    builder.append("(").append(time).append(")");
                }
                builder.append('\n');
                if (item.description() != null && !item.description().isBlank()) {
                    builder.append("  ").append(item.description()).append('\n');
                }
                if (item.tags() != null && !item.tags().isEmpty()) {
                    builder.append("  标签:").append(String.join("、", item.tags())).append('\n');
                }
            }
        }
        return builder.toString().strip();
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

    private static String timeRange(String start, String end) {
        String from = start == null ? "" : start.strip();
        String to = end == null ? "" : end.strip();
        if (from.isEmpty() && to.isEmpty()) {
            return "";
        }
        if (from.isEmpty()) {
            return to;
        }
        if (to.isEmpty()) {
            return from;
        }
        return from + " - " + to;
    }
}
