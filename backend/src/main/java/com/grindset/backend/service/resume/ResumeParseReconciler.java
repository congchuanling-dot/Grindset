package com.grindset.backend.service.resume;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.mapper.ResumeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 解析补偿任务:处理"落库成功但消息没发出去"或 worker 崩溃后的残留记录。
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParseReconciler {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseReconciler.class);

    private final ResumeMapper resumeMapper;
    private final ResumeParsePublisher publisher;
    private final ResumeParseService parseService;
    private final int stuckMinutes;
    private final int maxAttempts;

    public ResumeParseReconciler(ResumeMapper resumeMapper, ResumeParsePublisher publisher,
                                 ResumeParseService parseService,
                                 @Value("${app.resume.parse.stuck-minutes:10}") int stuckMinutes,
                                 @Value("${app.resume.parse.max-attempts:3}") int maxAttempts) {
        this.resumeMapper = resumeMapper;
        this.publisher = publisher;
        this.parseService = parseService;
        this.stuckMinutes = stuckMinutes;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(initialDelayString = "${app.resume.parse.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${app.resume.parse.reconcile-ms:300000}")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckMinutes);
        List<Resume> stuck = resumeMapper.selectList(new QueryWrapper<Resume>()
                .eq("parse_status", 0)
                .lt("updated_at", cutoff)
                .orderByAsc("updated_at")
                .last("LIMIT 100"));
        if (stuck.isEmpty()) {
            return;
        }
        log.info("发现 {} 条卡住的简历解析任务,开始补偿", stuck.size());
        for (Resume resume : stuck) {
            try {
                republish(resume);
            } catch (Exception e) {
                log.warn("补偿简历解析任务失败 resumeId={}", resume.getId(), e);
            }
        }
    }

    private void republish(Resume resume) {
        int attempt = resume.getParseAttempt() == null ? 0 : resume.getParseAttempt();
        if (resume.getOriginalObjectKey() == null || resume.getOriginalObjectKey().isBlank()) {
            parseService.markFailed(resume.getId(), "原件未存储，无法继续解析");
            return;
        }
        if (attempt >= maxAttempts) {
            parseService.markFailed(resume.getId(), "解析超时，已超过最大重试次数");
            return;
        }

        int nextAttempt = attempt + 1;
        Resume patch = new Resume();
        patch.setId(resume.getId());
        patch.setParseAttempt(nextAttempt);
        resumeMapper.updateById(patch);

        publisher.publish(publisher.toMessage(resume, nextAttempt));
        log.info("已重新投递简历解析任务 resumeId={} attempt={}", resume.getId(), nextAttempt);
    }
}
