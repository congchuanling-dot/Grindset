package com.grindset.backend.service.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表:只负责收集所有 {@link AgentTool} 实现并按名字查找。
 * 具体查询逻辑在 tool 包下,新增工具只需要加一个 @Component,这里不用改。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> agentTools) {
        for (AgentTool tool : agentTools) {
            // 启动时就去重:工具名冲突直接让应用起不来,比运行到一半才发现好
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Agent 工具名重复:" + tool.name());
            }
        }
    }

    /**
     * 由注册表统一生成 tools 载荷:工具名、说明、schema 只有实现类一个来源,
     * 不需要再在提示词里手抄一遍,新增工具也不会漏。
     */
    public List<Map<String, Object>> toolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>(tools.size());
        for (AgentTool tool : tools.values()) {
            definitions.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.parameters())));
        }
        return definitions;
    }

    /** 找不到返回 null,由调用方决定怎么提示模型 */
    public AgentTool find(String name) {
        return name == null ? null : tools.get(name);
    }
}
