package com.grindset.backend.llm;

import com.grindset.backend.common.AiBizType;

/**
 * 记账上下文:这次模型调用属于哪个用户、哪个业务场景。
 *
 * @param bizRef 业务主键(会话 ID / 面试 ID / 简历 ID),没有就传 null
 */
public record LlmUsageContext(long userId, AiBizType bizType, String bizRef) {
}
