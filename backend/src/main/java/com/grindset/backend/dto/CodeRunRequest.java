package com.grindset.backend.dto;

/**
 * 运行样例 / 提交代码请求体。
 */
public record CodeRunRequest(
        String language,
        String code) {
}
