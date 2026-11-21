package com.grindset.backend.service.resume;

import com.grindset.backend.config.RabbitResumeConfig;
import com.grindset.backend.dto.ResumeParseMessage;
import com.grindset.backend.entity.Resume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 简历解析任务生产者。只负责把 resumeId 等元数据投递给 RabbitMQ。
 */
@Service
public class ResumeParsePublisher {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ResumeParsePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setReturnsCallback(returned -> log.error(
                "简历解析消息不可路由 exchange={} routingKey={} reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    /** 初次投递,attempt=1 表示第 1 次解析 */
    public void publish(Resume resume) {
        publish(toMessage(resume, 1));
    }

    public void publish(ResumeParseMessage message) {
        send(RabbitResumeConfig.EXCHANGE, RabbitResumeConfig.ROUTING, message);
    }

    /** 临时失败后按次数选择延迟队列;超过上限进死信 */
    public void retry(ResumeParseMessage message, int nextAttempt) {
        String routingKey = switch (nextAttempt) {
            case 1 -> RabbitResumeConfig.RETRY_30S_ROUTING;
            case 2 -> RabbitResumeConfig.RETRY_2M_ROUTING;
            case 3 -> RabbitResumeConfig.RETRY_10M_ROUTING;
            default -> RabbitResumeConfig.DLQ_ROUTING;
        };
        send(RabbitResumeConfig.EXCHANGE, routingKey, withAttempt(message, nextAttempt));
    }

    /** 延迟重投但不消耗尝试次数,用于并发锁冲突等场景 */
    public void delay(ResumeParseMessage message, String routingKey) {
        send(RabbitResumeConfig.EXCHANGE, routingKey, message);
    }

    /** 永久失败直接进死信队列 */
    public void deadLetter(ResumeParseMessage message) {
        send(RabbitResumeConfig.EXCHANGE, RabbitResumeConfig.DLQ_ROUTING, message);
    }

    public ResumeParseMessage toMessage(Resume resume, int attempt) {
        return new ResumeParseMessage(
                String.valueOf(resume.getId()),
                resume.getUserId() == null ? null : String.valueOf(resume.getUserId()),
                resume.getOriginalObjectKey(),
                resume.getFileType() == null ? null : resume.getFileType().name(),
                resume.getFileName(),
                attempt,
                UUID.randomUUID().toString());
    }

    private ResumeParseMessage withAttempt(ResumeParseMessage message, int attempt) {
        return new ResumeParseMessage(message.resumeId(), message.userId(), message.objectKey(),
                message.fileType(), message.fileName(), attempt, message.traceId());
    }

    private void send(String exchange, String routingKey, ResumeParseMessage message) {
        CorrelationData correlation = new CorrelationData(message.resumeId() + ":" + message.attempt());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation);
            correlation.getFuture().whenComplete((confirm, error) -> {
                if (error != null) {
                    log.error("简历解析消息发送异常 resumeId={} routingKey={}", message.resumeId(), routingKey, error);
                } else if (confirm == null || !confirm.isAck()) {
                    log.error("简历解析消息未被 Broker 确认 resumeId={} routingKey={} reason={}",
                            message.resumeId(), routingKey, confirm == null ? "null" : confirm.getReason());
                }
            });
        } catch (RuntimeException e) {
            log.error("简历解析消息发送失败 resumeId={} routingKey={}", message.resumeId(), routingKey, e);
            throw e;
        }
    }
}
