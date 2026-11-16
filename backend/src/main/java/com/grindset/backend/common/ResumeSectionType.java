package com.grindset.backend.common;

/**
 * 简历章节类型。LLM 只能返回这些值,未知值统一降级为 OTHER。
 * 新增章节类型时只改这个枚举,数据库不需要迁移。
 */
public enum ResumeSectionType {

    EDUCATION("教育经历"),
    WORK("工作经历"),
    INTERNSHIP("实习经历"),
    PROJECT("项目经历"),
    RESEARCH("科研经历"),
    AWARD("获奖经历"),
    CAMPUS("校园经历"),
    CERTIFICATE("证书"),
    SUMMARY("个人总结"),
    OTHER("其他");

    private final String defaultTitle;

    ResumeSectionType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    /** LLM 没给标题时用的兜底标题 */
    public String defaultTitle() {
        return defaultTitle;
    }

    /** 忽略大小写匹配,不认识的一律 OTHER */
    public static ResumeSectionType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
