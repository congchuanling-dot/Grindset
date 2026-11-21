package com.grindset.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 简历解析任务拓扑。
 *
 * <p>主队列使用 quorum 持久化;失败任务按 30s / 2m / 10m 三档 TTL 重试,
 * 超过次数由 Consumer 直接投递到死信队列,避免 requeue 紧循环。</p>
 */
@Configuration
public class RabbitResumeConfig {

    /** 主交换机:生产者投递、重试队列回投、死信投递都经过它 */
    public static final String EXCHANGE = "resume.exchange";

    /** 主队列的 routing key:正常解析任务用它路由到主队列 */
    public static final String ROUTING = "resume.parse";

    /** 主队列:worker 监听这里消费解析任务 */
    public static final String QUEUE = "resume.parse.queue";

    /** 死信交换机:主队列 reject 的消息会转到这里 */
    public static final String DLX = "resume.dlx";

    /** 死信队列:超过重试次数或永久失败的消息最终落在这里,供人工排查和重投 */
    public static final String DLQ = "resume.parse.dlq";

    /** 死信 routing key:主队列的 DLX 用它把消息投进死信队列 */
    public static final String DLQ_ROUTING = "resume.parse.dead";

    /** 第 1 档重试:延迟 30 秒后回主队列;该值同时用作重试队列名 */
    public static final String RETRY_30S_ROUTING = "resume.parse.retry.30s";

    /** 第 2 档重试:延迟 2 分钟后回主队列 */
    public static final String RETRY_2M_ROUTING = "resume.parse.retry.2m";

    /** 第 3 档重试:延迟 10 分钟后回主队列 */
    public static final String RETRY_10M_ROUTING = "resume.parse.retry.10m";

    @Bean
    public DirectExchange resumeExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange resumeDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue resumeParseQueue() {
        return QueueBuilder.durable(QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING)
                .build();
    }

    @Bean
    public Queue resumeParseDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).quorum().build();
    }

    @Bean
    public Queue resumeParseRetry30sQueue() {
        return retryQueue(RETRY_30S_ROUTING, 30_000);
    }

    @Bean
    public Queue resumeParseRetry2mQueue() {
        return retryQueue(RETRY_2M_ROUTING, 120_000);
    }

    @Bean
    public Queue resumeParseRetry10mQueue() {
        return retryQueue(RETRY_10M_ROUTING, 600_000);
    }

    @Bean
    public Binding resumeParseBinding() {
        return BindingBuilder.bind(resumeParseQueue()).to(resumeExchange()).with(ROUTING);
    }

    @Bean
    public Binding resumeParseDeadLetterBinding() {
        return BindingBuilder.bind(resumeParseDeadLetterQueue())
                .to(resumeDeadLetterExchange()).with(DLQ_ROUTING);
    }

    @Bean
    public Binding resumeParseRetry30sBinding() {
        return BindingBuilder.bind(resumeParseRetry30sQueue())
                .to(resumeExchange()).with(RETRY_30S_ROUTING);
    }

    @Bean
    public Binding resumeParseRetry2mBinding() {
        return BindingBuilder.bind(resumeParseRetry2mQueue())
                .to(resumeExchange()).with(RETRY_2M_ROUTING);
    }

    @Bean
    public Binding resumeParseRetry10mBinding() {
        return BindingBuilder.bind(resumeParseRetry10mQueue())
                .to(resumeExchange()).with(RETRY_10M_ROUTING);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** 重试队列:TTL 到期后通过 DLX 回到主交换机 */
    private static Queue retryQueue(String routingKey, int ttlMillis) {
        return QueueBuilder.durable(routingKey)
                .ttl(ttlMillis)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING)
                .build();
    }
}
