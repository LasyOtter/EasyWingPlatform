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
package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.tools.ToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatModel chatLanguageModel;
    private final ToolRegistry toolRegistry;
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();

    public AgentService(ChatModel chatLanguageModel, ToolRegistry toolRegistry) {
        this.chatLanguageModel = chatLanguageModel;
        this.toolRegistry = toolRegistry;
        log.info("AgentService initialized");
    }

    public String chat(String userId, String message) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(userId, k -> new ArrayList<>());

        history.add(new ChatMessage("user", message));

        String response = doChat(history);

        history.add(new ChatMessage("assistant", response));

        return response;
    }

    public String chatWithTools(String userId, String message) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(userId, k -> new ArrayList<>());

        history.add(new ChatMessage("user", message));

        String response = doChatWithTools(history);

        history.add(new ChatMessage("assistant", response));

        return response;
    }

    private String doChat(List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage msg : history) {
            prompt.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        prompt.append("assistant: ");

        return chatLanguageModel.chat(prompt.toString());
    }

    private String doChatWithTools(List<ChatMessage> history) {
        return doChat(history);
    }

    public void clearMemory(String userId) {
        chatHistories.remove(userId);
        log.info("Cleared agent memory for user: {}", userId);
    }

    public void clearAllMemory() {
        chatHistories.clear();
        log.info("Cleared all agent memory");
    }

    public void registerTool(String name, Object tool) {
        toolRegistry.registerTool(name, tool);
    }

    public record ChatMessage(String role, String content) {}
}
