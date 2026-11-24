package com.grindset.backend.dto;

/**
 * 题库配图上传结果。url 指向后端的公开图片代理,私有桶也能直接展示。
 */
public record QuestionImageUploadResult(String key, String url) {
}
