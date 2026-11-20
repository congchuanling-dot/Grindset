package com.grindset.backend.service.agent;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 同一会话同时只允许一个生成任务。
 *
 * <p>两个请求同时往一个会话里追加消息会抢 seq,还可能让模型看到半截上下文;
 * 前端虽然会禁用切换,但后端仍要兜底。新会话在创建后才有 ID,自然不冲突。</p>
 */
@Component
public class AgentConversationLocks {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockOf(long conversationId) {
        return locks.computeIfAbsent(conversationId, key -> new ReentrantLock());
    }
}
