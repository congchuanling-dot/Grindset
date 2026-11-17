package com.grindset.backend.dto;

import java.util.List;

/**
 * 运行样例的结果:逐用例输出与期望对比。
 * 只跑例题,便于用户自查,不计入提交历史。
 */
public record CodeRunResult(
        String status,
        int passed,
        int total,
        int runtimeMs,
        String errorMessage,
        List<CaseResult> cases) {

    public record CaseResult(
            int index,
            String input,
            String expectedOutput,
            String actualOutput,
            boolean passed,
            String message) {
    }
}
