package com.grindset.backend.service.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.entity.InterviewMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 面试视图转换的公共方法,会话详情和出题返回都要用。
 */
final class InterviewViews {

    private static final Logger log = LoggerFactory.getLogger(InterviewViews.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private InterviewViews() {
    }

    static InterviewDetail.MessageView toView(InterviewMessage message) {
        return new InterviewDetail.MessageView(String.valueOf(message.getId()), intValue(message.getSeq()),
                message.getRole(), message.getType(), message.getContent(), format(message.getCreatedAt()));
    }

    /** 报告 JSON 坏掉时返回 null,调用方按"没有报告"处理 */
    static InterviewDetail.ReportView parseReport(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, InterviewDetail.ReportView.class);
        } catch (JsonProcessingException e) {
            log.warn("复盘报告 JSON 解析失败", e);
            return null;
        }
    }

    static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    static String format(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FORMAT);
    }
}
