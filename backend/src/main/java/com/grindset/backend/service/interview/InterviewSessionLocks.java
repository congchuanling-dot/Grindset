package com.grindset.backend.service.interview;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 同一场面试的出题、答题、复盘必须互斥。
 * 拆成多个 service 后锁不能各自持有一份,否则复盘和答题可能同时改同一条会话。
 */
@Component
public class InterviewSessionLocks {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockOf(long sessionId) {
        return locks.computeIfAbsent(sessionId, key -> new ReentrantLock());
    }
}
