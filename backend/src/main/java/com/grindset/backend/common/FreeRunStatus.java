package com.grindset.backend.common;

/**
 * FREE_RUN 面试编码的执行结果。只表示代码是否成功跑完,不表示答案是否正确。
 */
public enum FreeRunStatus {

    SUCCESS,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    SYSTEM_ERROR;

    public boolean success() {
        return this == SUCCESS;
    }
}
