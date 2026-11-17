package com.grindset.backend.service.agent.tool;

import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.service.InterviewService;
import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最近的 AI 面试记录。
 */
@Component
public class RecentInterviewsTool implements AgentTool {

    private final InterviewService interviewService;

    public RecentInterviewsTool(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @Override
    public String name() {
        return "list_recent_interviews";
    }

    @Override
    public String label() {
        return "查询面试记录";
    }

    @Override
    public String description() {
        return "查询最近的 AI 模拟面试记录。可选参数:limit(默认5,最多20)。"
                + "返回岗位、轮次、状态(0进行中/1已结束)、总分、完成题数/总题数、创建时间。";
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        int limit = AgentToolArgs.integer(args, "limit", 5, 1, 20);
        List<InterviewSummary> all = interviewService.list(userId, null);
        List<InterviewSummary> items = all.size() > limit ? all.subList(0, limit) : all;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", all.size());
        data.put("items", items);

        String summary = all.isEmpty()
                ? "还没有面试记录"
                : String.format("共 %d 场面试,本次返回最近 %d 场", all.size(), items.size());
        return new AgentToolResult(true, summary, data);
    }
}
