package com.grindset.backend.dto;

/**
 * 运行样例 / 提交代码请求体。
 * codeMode 为空时按题目的默认模式(ACM)处理。
 */
public record CodeRunRequest(
        String language,
        String code,
        String codeMode) {
}
