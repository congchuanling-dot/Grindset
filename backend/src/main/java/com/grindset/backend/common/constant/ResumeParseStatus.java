package com.grindset.backend.common.constant;

/**
 * 简历解析状态,对应 resumes.parse_status 列。
 */
public final class ResumeParseStatus {

    /** 解析中(落库时的初始值) */
    public static final int PARSING = 0;

    /** 解析成功,章节与条目已写入 */
    public static final int SUCCESS = 1;

    /** 解析失败,error_msg 中有原因 */
    public static final int FAILED = 2;

    private ResumeParseStatus() {
    }
}
