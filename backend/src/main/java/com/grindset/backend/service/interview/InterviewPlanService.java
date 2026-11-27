package com.grindset.backend.service.interview;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.InterviewStyle;
import com.grindset.backend.common.JobLevel;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.InterviewPlan;
import com.grindset.backend.dto.InterviewPlanPreview;
import com.grindset.backend.dto.InterviewPlanRequest;
import com.grindset.backend.dto.JdProfile;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.service.ResumeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 面试计划预览:只解析 JD、给推荐配置,不创建会话。
 * 计划是"用户确认"的中间产物,真正落库仍走 InterviewSessionService。
 */
@Service
public class InterviewPlanService {

    private static final List<Integer> DURATIONS = List.of(15, 25, 40);
    private static final List<Integer> QUESTION_COUNTS = List.of(3, 5, 8);

    private final ResumeService resumeService;
    private final InterviewJdService jdService;

    public InterviewPlanService(ResumeService resumeService, InterviewJdService jdService) {
        this.resumeService = resumeService;
        this.jdService = jdService;
    }

    public InterviewPlanPreview preview(long userId, InterviewPlanRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        long resumeId = parseId(request.getResumeId());
        ResumeDetail resume = resumeService.get(userId, resumeId);
        if (resume.parseStatus() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这份简历还没有解析成功,请先重新上传");
        }

        String jdText = jdService.normalizeJdText(request.getJdText());
        String company = jdService.normalizeCompany(request.getCompany());
        String mode = normalizeMode(request.getMode(), jdText);

        JobLevel level = JobLevel.from(request.getJobLevel());
        if (request.getJobLevel() != null && !request.getJobLevel().isBlank() && level == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "岗位级别不合法");
        }
        if (level == null) {
            level = JobLevel.fromCandidateType(resume.candidateType());
        }

        String position = Texts.blankToNull(request.getPosition());
        JdProfile profile = null;
        if (!"quick".equals(mode) && jdText != null) {
            profile = jdService.parseProfile(userId, resumeId,
                    position == null ? "目标岗位" : position, jdText, company, level);
        }
        if (position == null && profile != null) {
            position = profile.title();
        }
        if (position == null) {
            position = "目标岗位";
        }
        position = Texts.truncate(position, 64);

        int durationMin = normalizeDuration(request.getDurationMin(), request.getQuestionCount());
        int questionCount = normalizeQuestionCount(request.getQuestionCount(), durationMin);
        String round = normalizeRound(request.getRound(), level, mode);
        String style = normalizeStyle(request.getStyle(), level, mode);

        // 产品约定:只要不是 HR 面,技术面都必须包含一道手撕代码;HR 面自动关闭
        boolean coding = !round.contains("HR") && questionCount > 1;

        List<String> focus = normalizeFocus(request.getFocus());
        if (focus.isEmpty() && profile != null) {
            focus = topNames(profile, 3);
        }
        if (focus.isEmpty()) {
            focus = defaultFocus(mode, round);
        }

        List<InterviewPlan.Competency> competencies =
                buildCompetencies(round.contains("HR") ? null : profile, focus, mode);
        List<InterviewPlan.Item> mix = buildMix(mode, round, questionCount, coding, level);

        List<String> rationale = new ArrayList<>();
        rationale.add("按" + (level == null ? "目标岗位" : level.label()) + "推荐:" + round);
        if (profile != null && !round.contains("HR")) {
            rationale.add("JD 重点能力:" + joinNames(competencies));
        }
        if ("quick".equals(mode)) {
            rationale.add("未使用 JD,按简历与岗位自动生成");
        }
        if ("drill".equals(mode)) {
            rationale.add("专项训练只围绕选中的能力项出题，并保留一道手撕代码");
        }
        if (round.contains("HR")) {
            rationale.add("HR 面不包含手撕代码");
        } else {
            rationale.add("技术面默认包含 1 道手撕代码");
        }
        rationale.add("预计 " + durationMin + " 分钟," + questionCount + " 题");

        List<String> warnings = new ArrayList<>();
        if (jdText != null && profile == null) {
            warnings.add("JD 解析失败,本次按岗位名称与简历生成");
        }
        if (jdText == null && "jd".equals(mode)) {
            warnings.add("未粘贴 JD,已按岗位名称生成");
        }
        if (coding && questionCount <= 3) {
            warnings.add("3 题含 1 道手撕代码，口述题只有 2 道");
        }
        if (questionCount >= 8 && durationMin < 40) {
            warnings.add("8 题建议至少 40 分钟");
        }

        InterviewPlan plan = new InterviewPlan(mode, round, durationMin, questionCount, mix,
                competencies, style, rationale, warnings);
        return new InterviewPlanPreview(position, profile, plan);
    }

    private static int normalizeDuration(Integer duration, Integer questionCount) {
        if (duration != null && DURATIONS.contains(duration)) {
            return duration;
        }
        if (questionCount != null) {
            if (questionCount <= 3) {
                return 15;
            }
            if (questionCount >= 8) {
                return 40;
            }
        }
        return 25;
    }

    private static int normalizeQuestionCount(Integer questionCount, int durationMin) {
        if (questionCount != null && QUESTION_COUNTS.contains(questionCount)) {
            return questionCount;
        }
        if (durationMin <= 15) {
            return 3;
        }
        return durationMin >= 40 ? 8 : 5;
    }

    private static String normalizeMode(String raw, String jdText) {
        if (raw != null && !raw.isBlank()) {
            String value = raw.strip().toLowerCase();
            if ("quick".equals(value) || "jd".equals(value) || "drill".equals(value)) {
                return value;
            }
        }
        return jdText == null ? "quick" : "jd";
    }

    private static String normalizeRound(String raw, JobLevel level, String mode) {
        if (raw != null && !raw.isBlank() && raw.strip().length() <= 32) {
            return raw.strip();
        }
        if ("drill".equals(mode)) {
            return "专项训练 · 定向补强";
        }
        if (level == null || level == JobLevel.INTERN || level == JobLevel.CAMPUS) {
            return "一面 · 技术基础";
        }
        return "二面 · 项目深挖";
    }

    private static String normalizeStyle(String raw, JobLevel level, String mode) {
        InterviewStyle style = InterviewStyle.from(raw);
        if (style != null) {
            return style.code();
        }
        if ("drill".equals(mode) || level == JobLevel.INTERN) {
            return InterviewStyle.RELAXED.code();
        }
        if (level == JobLevel.SENIOR) {
            return InterviewStyle.PRESSURE.code();
        }
        return InterviewStyle.MIDDLE.code();
    }

    private static List<String> defaultFocus(String mode, String round) {
        if ("drill".equals(mode)) {
            return new ArrayList<>(List.of("项目深挖"));
        }
        if (round.contains("HR")) {
            return new ArrayList<>(List.of("行为面", "动机与职业规划"));
        }
        return new ArrayList<>(List.of("项目深挖", "基础八股"));
    }

    private static List<InterviewPlan.Item> buildMix(String mode, String round, int total,
                                                     boolean coding, JobLevel level) {
        List<InterviewPlan.Item> items = new ArrayList<>();
        if ("drill".equals(mode)) {
            int codingCount = coding ? 1 : 0;
            int drill = total - codingCount;
            if (drill > 0) {
                items.add(new InterviewPlan.Item("DRILL", "专项能力", drill));
            }
            if (codingCount > 0) {
                items.add(new InterviewPlan.Item("CODING", "手撕代码", 1));
            }
            return items;
        }
        if (round.contains("HR")) {
            int behavior = Math.max(1, (int) Math.ceil(total * 0.6));
            int motivation = total - behavior;
            items.add(new InterviewPlan.Item("BEHAVIOR", "行为面", behavior));
            if (motivation > 0) {
                items.add(new InterviewPlan.Item("MOTIVATION", "动机与职业规划", motivation));
            }
            return items;
        }

        int codingCount = coding ? 1 : 0;
        int verbal = total - codingCount;
        int project;
        int basics = 0;
        int system = 0;
        if (level == JobLevel.SENIOR) {
            project = Math.max(1, Math.round(verbal * 0.4f));
            system = Math.max(1, Math.round(verbal * 0.4f));
            basics = verbal - project - system;
            if (basics < 0) {
                basics = 0;
                project = Math.max(1, verbal - system);
            }
        } else {
            project = Math.max(1, Math.round(verbal * 0.5f));
            basics = verbal - project;
        }
        if (project > 0) {
            items.add(new InterviewPlan.Item("PROJECT", "项目深挖", project));
        }
        if (basics > 0) {
            items.add(new InterviewPlan.Item("BASICS", "基础八股", basics));
        }
        if (system > 0) {
            items.add(new InterviewPlan.Item("SYSTEM", "系统设计", system));
        }
        if (codingCount > 0) {
            items.add(new InterviewPlan.Item("CODING", "手撕代码", 1));
        }
        return items;
    }

    private static List<InterviewPlan.Competency> buildCompetencies(JdProfile profile,
                                                                    List<String> focus, String mode) {
        if (profile != null && profile.competencies() != null && !profile.competencies().isEmpty()) {
            List<InterviewPlan.Competency> result = new ArrayList<>();
            for (String name : focus) {
                JdProfile.Competency matched = findCompetency(profile, name);
                if (matched != null) {
                    result.add(new InterviewPlan.Competency(matched.name(),
                            matched.weight() == null ? 0 : matched.weight(), "JD"));
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
            return topCompetencies(profile, 3);
        }
        if (!focus.isEmpty()) {
            int base = Math.max(1, 100 / focus.size());
            List<InterviewPlan.Competency> result = new ArrayList<>();
            for (int i = 0; i < focus.size(); i++) {
                int weight = i == 0 ? 100 - base * (focus.size() - 1) : base;
                result.add(new InterviewPlan.Competency(focus.get(i), weight,
                        "drill".equals(mode) ? "专项" : "自选"));
            }
            return result;
        }
        return List.of(
                new InterviewPlan.Competency("项目深挖", 40, "系统推荐"),
                new InterviewPlan.Competency("基础八股", 30, "系统推荐"),
                new InterviewPlan.Competency("系统设计", 30, "系统推荐"));
    }

    private static JdProfile.Competency findCompetency(JdProfile profile, String name) {
        if (profile == null || profile.competencies() == null) {
            return null;
        }
        for (JdProfile.Competency item : profile.competencies()) {
            if (item != null && item.name() != null
                    && (item.name().equals(name) || item.name().contains(name) || name.contains(item.name()))) {
                return item;
            }
        }
        return null;
    }

    private static List<InterviewPlan.Competency> topCompetencies(JdProfile profile, int limit) {
        List<JdProfile.Competency> sorted = new ArrayList<>(profile.competencies());
        sorted.sort(Comparator.comparingInt(item -> item.weight() == null ? 0 : item.weight()));
        java.util.Collections.reverse(sorted);
        List<InterviewPlan.Competency> result = new ArrayList<>();
        for (JdProfile.Competency item : sorted) {
            if (item.name() == null) {
                continue;
            }
            result.add(new InterviewPlan.Competency(item.name(),
                    item.weight() == null ? 0 : item.weight(), "JD"));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static List<String> topNames(JdProfile profile, int limit) {
        List<String> names = new ArrayList<>();
        for (InterviewPlan.Competency competency : topCompetencies(profile, limit)) {
            names.add(competency.name());
        }
        return names;
    }

    private static List<String> normalizeFocus(List<String> focus) {
        List<String> result = new ArrayList<>();
        if (focus == null) {
            return result;
        }
        for (String item : focus) {
            String value = Texts.blankToNull(item);
            if (value == null || result.contains(value)) {
                continue;
            }
            result.add(Texts.truncate(value, 32));
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private static String joinNames(List<InterviewPlan.Competency> competencies) {
        if (competencies == null || competencies.isEmpty()) {
            return "无";
        }
        List<String> names = new ArrayList<>();
        for (InterviewPlan.Competency item : competencies) {
            names.add(item.name());
        }
        return String.join("、", names);
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.strip());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历 ID 不合法");
        }
    }
}
