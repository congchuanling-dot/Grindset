package com.grindset.backend.service.agent;

import org.springframework.stereotype.Component;

import java.util.Collection;
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
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Agent 工具名重复:" + tool.name());
            }
        }
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    /** 找不到返回 null,由调用方决定怎么提示模型 */
    public AgentTool find(String name) {
        return name == null ? null : tools.get(name);
    }
}
