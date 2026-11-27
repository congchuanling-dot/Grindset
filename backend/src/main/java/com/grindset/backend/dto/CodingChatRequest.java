package com.grindset.backend.dto;

/**
 * 手撕代码阶段的提问请求。
 * 前端每次带上当前编辑器里的代码,面试官才能基于最新实现回答。
 */
public record CodingChatRequest(
        String message,
        String code,
        String language,
        String codeMode) {
}
