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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.grindset.backend.common.constant.ResumeParseStatus.PARSING;

/**
 * 简历解析消费者。api 实例通过 WORKER_ENABLED=false 关闭消费。
 */
@Component
@ConditionalOnProperty(name = "app.resume.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParseConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseConsumer.class);

    private static final String LOCK_PREFIX = "resume:parse:";

    /** 只有锁的值仍是自己写入的 token 才删除,避免误删其它 worker 续上的锁 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ResumeParseService parseService;
    private final ResumeParsePublisher publisher;
    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration lockTtl;

    public ResumeParseConsumer(ResumeParseService parseService, ResumeParsePublisher publisher,
                               StringRedisTemplate redis,
                               @Value("${app.resume.parse.max-attempts:4}") int maxAttempts,
                               @Value("${app.resume.parse.lock-ttl-minutes:10}") int lockTtlMinutes) {
        this.parseService = parseService;
        this.publisher = publisher;
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.lockTtl = Duration.ofMinutes(Math.max(1, lockTtlMinutes));
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
        if (message.attempt() <= 0) {
            log.error("简历解析消息 attempt 非法,进入死信 resumeId={} attempt={}", resumeId, message.attempt());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String lockKey = LOCK_PREFIX + resumeId;
        String lockToken = tryLock(lockKey);
        if (lockToken == null) {
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
            if (resume.getParseStatus() != null && resume.getParseStatus() != PARSING) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 补偿任务可能已经把 attempt 推进,旧消息不能把进度改回去
            int currentAttempt = resume.getParseAttempt() == null ? 0 : resume.getParseAttempt();
            if (message.attempt() < currentAttempt) {
                log.info("跳过过期解析消息 resumeId={} messageAttempt={} currentAttempt={}",
                        resumeId, message.attempt(), currentAttempt);
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
            releaseLock(lockKey, lockToken);
        }
    }

    /** Redis 不可用时退化为不加锁,不阻断解析 */
    private String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, lockTtl);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (RuntimeException e) {
            log.warn("简历解析锁不可用,继续执行 key={}", key, e);
            return token;
        }
    }

    private void releaseLock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("释放简历解析锁失败 key={}", key, e);
        }
    }
}
