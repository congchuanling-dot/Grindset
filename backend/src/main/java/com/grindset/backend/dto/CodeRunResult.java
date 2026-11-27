package com.grindset.backend.dto;

/**
 * 自由运行结果:只表示程序是否跑完,不判断答案对错。
 * runStatus 取值见 FreeRunStatus:SUCCESS / COMPILE_ERROR / RUNTIME_ERROR /
 * TIME_LIMIT_EXCEEDED / SYSTEM_ERROR。
 */
public record CodeRunResult(
        String runStatus,
        String stdout,
        String stderr,
        int exitCode,
        int runtimeMs) {
}
