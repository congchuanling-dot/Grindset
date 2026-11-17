package com.grindset.backend.common.constant;

/**
 * 编程题判题状态,对应 question_submissions.status 列。
 *
 * <p>提交接口先落 PENDING,后台线程改为 RUNNING,结束后写入终态。
 * 前端轮询时按终态判断是否停止。</p>
 */
public final class SubmissionStatus {

    /** 已受理,等待判题线程处理 */
    public static final String PENDING = "PENDING";

    /** 正在编译或执行 */
    public static final String RUNNING = "RUNNING";

    /** 全部用例通过 */
    public static final String ACCEPTED = "ACCEPTED";

    /** 用例结果不符合预期 */
    public static final String WRONG_ANSWER = "WRONG_ANSWER";

    /** 编译失败(仅编译型语言) */
    public static final String COMPILE_ERROR = "COMPILE_ERROR";

    /** 运行时异常或非零退出码 */
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";

    /** 单个用例执行超时 */
    public static final String TIME_LIMIT_EXCEEDED = "TIME_LIMIT_EXCEEDED";

    /** 判题基础设施异常(Docker 不可用等) */
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    private SubmissionStatus() {
    }
}
