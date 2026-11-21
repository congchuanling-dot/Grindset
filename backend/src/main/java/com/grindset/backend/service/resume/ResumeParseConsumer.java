package com.grindset.backend.service.resume;

import com.grindset.backend.config.RabbitResumeConfig;
import com.grindset.backend.dto.ResumeParseMessage;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.service.resume.ResumeParseService.ParseOutcome;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 简历解析消费者。api 实例通过 WORKER_ENABLED=false 关闭消费。
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParseConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseConsumer.class);

    private static final String LOCK_PREFIX = "resume:parse:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final ResumeParseService parseService;
    private final ResumeParsePublisher publisher;
    private final StringRedisTemplate redis;
    private final int maxAttempts;

    public ResumeParseConsumer(ResumeParseService parseService, ResumeParsePublisher publisher,
                               StringRedisTemplate redis,
                               @Value("${app.resume.parse.max-attempts:3}") int maxAttempts) {
        this.parseService = parseService;
        this.publisher = publisher;
        this.redis = redis;
        this.maxAttempts = maxAttempts;
    }

    @RabbitListener(queues = RabbitResumeConfig.QUEUE)
    public void consume(ResumeParseMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        long resumeId;
        try {
            resumeId = Long.parseLong(message.resumeId());
        } catch (RuntimeException e) {
            log.error("简历解析消息 resumeId 非法,进入死信 queue={}", message.resumeId());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String lockKey = LOCK_PREFIX + resumeId;
        boolean locked = tryLock(lockKey, message.traceId());
        if (!locked) {
            // 同一份简历正在被其它线程处理,30 秒后再看
            try {
                publisher.delay(message, RabbitResumeConfig.RETRY_30S_ROUTING);
                channel.basicAck(deliveryTag, false);
            } catch (RuntimeException e) {
                log.warn("并发锁冲突后延迟重投失败 resumeId={}", resumeId, e);
                channel.basicNack(deliveryTag, false, true);
            }
            return;
        }

        try {
            Resume resume = parseService.find(resumeId);
            if (resume == null) {
                log.warn("简历不存在,消息进入死信 resumeId={}", resumeId);
                publisher.deadLetter(message);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (message.userId() != null && resume.getUserId() != null
                    && !message.userId().equals(String.valueOf(resume.getUserId()))) {
                log.warn("简历归属与消息不一致,忽略 resumeId={} messageUserId={}", resumeId, message.userId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 成功或已永久失败都不再重复解析
            if (resume.getParseStatus() != null && resume.getParseStatus() != 0) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            ParseOutcome outcome = parseService.parse(resume, message.attempt());
            if (outcome.success()) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (outcome.retryable() && message.attempt() + 1 <= maxAttempts) {
                publisher.retry(message, message.attempt() + 1);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (outcome.retryable()) {
                parseService.markFailed(resumeId, outcome.errorMessage());
            }
            publisher.deadLetter(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("简历解析消费异常 resumeId={} attempt={}", resumeId, message.attempt(), e);
            int nextAttempt = message.attempt() + 1;
            try {
                if (nextAttempt <= maxAttempts) {
                    publisher.retry(message, nextAttempt);
                } else {
                    parseService.markFailed(resumeId, "解析失败：" + e.getMessage());
                    publisher.deadLetter(message);
                }
                channel.basicAck(deliveryTag, false);
            } catch (RuntimeException retryError) {
                log.error("简历解析重试投递失败,消息重回队列 resumeId={}", resumeId, retryError);
                channel.basicNack(deliveryTag, false, true);
            }
        } finally {
            releaseLock(lockKey);
        }
    }

    /** Redis 不可用时退化为不加锁,不阻断解析 */
    private boolean tryLock(String key, String traceId) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, traceId == null ? "1" : traceId, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.warn("简历解析锁不可用,继续执行 key={}", key, e);
            return true;
        }
    }

    private void releaseLock(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("释放简历解析锁失败 key={}", key, e);
        }
    }
}
