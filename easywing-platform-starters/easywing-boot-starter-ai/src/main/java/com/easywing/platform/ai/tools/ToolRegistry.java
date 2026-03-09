/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Object> tools = new ConcurrentHashMap<>();
    private final List<String> includePackages;

    public ToolRegistry(List<String> includePackages) {
        this.includePackages = includePackages != null ? includePackages : List.of();
        log.info("ToolRegistry initialized with packages: {}", includePackages);
    }

    public void registerTool(String name, Object tool) {
        tools.put(name, tool);
        log.info("Registered tool: {}", name);
    }

    public void unregisterTool(String name) {
        tools.remove(name);
        log.info("Unregistered tool: {}", name);
    }

    public Object getTool(String name) {
        return tools.get(name);
    }

    public List<Object> getTools() {
        return new ArrayList<>(tools.values());
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public Map<String, Object> getAllTools() {
        return Map.copyOf(tools);
    }

    public void scanAndRegister(ApplicationContext applicationContext, String... basePackages) {
        log.info("Scanning for tools in packages: {}", String.join(", ", basePackages));

        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();

                if (isToolClass(beanClass)) {
                    String toolName = extractToolName(beanClass);
                    registerTool(toolName, bean);
                }
            } catch (Exception e) {
                log.trace("Skipping bean {} - not a tool", beanName);
            }
        }
    }

    private boolean isToolClass(Class<?> clazz) {
        if (clazz.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
            return true;
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                return true;
            }
        }

        return false;
    }

    private String extractToolName(Class<?> clazz) {
        dev.langchain4j.agent.tool.Tool toolAnnotation =
            clazz.getAnnotation(dev.langchain4j.agent.tool.Tool.class);

        if (toolAnnotation != null && !toolAnnotation.name().isEmpty()) {
            return toolAnnotation.name();
        }

        String simpleName = clazz.getSimpleName();
        if (simpleName.endsWith("Tool")) {
            return simpleName.substring(0, simpleName.length() - 4).toLowerCase();
        }

        return simpleName.toLowerCase();
    }
}
