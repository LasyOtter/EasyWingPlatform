package com.easywing.platform.ai.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public void register(ToolDefinition tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public ToolDefinition get(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> getAll() {
        return List.copyOf(tools.values());
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public int size() {
        return tools.size();
    }
}