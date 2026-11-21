package com.grindset.backend.common.constant;

/**
 * 编程题判题状态,对应 question_submissions.status 列。
 *
 * <p>提交接口先落 PENDING,后台线程改为 RUNNING,结束后写入终态。
 * 前端轮询时按终态判断是否停止。</p>
 */
public enum SubmissionStatus {

    /** 已受理,等待判题线程处理 */
    PENDING,

    /** 正在编译或执行 */
    RUNNING,

    /** 全部用例通过 */
    ACCEPTED,

    /** 用例结果不符合预期 */
    WRONG_ANSWER,

    /** 编译失败(仅编译型语言) */
    COMPILE_ERROR,

    /** 运行时异常或非零退出码 */
    RUNTIME_ERROR,

    /** 单个用例执行超时 */
    TIME_LIMIT_EXCEEDED,

    /** 判题基础设施异常(Docker 不可用等) */
    SYSTEM_ERROR;

    /** 是否终态,前端可停止轮询 */
    public boolean finished() {
        return this != PENDING && this != RUNNING;
    }
}
