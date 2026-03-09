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
package com.easywing.platform.ai.client;

import com.easywing.platform.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatAiClient {

    private static final Logger log = LoggerFactory.getLogger(ChatAiClient.class);

    private final AiProperties properties;
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatAiClient(AiProperties properties) {
        this.properties = properties;
    }

    public String chat(String userId, String message) {
        ChatSession session = sessions.computeIfAbsent(userId, ChatSession::new);
        return session.chat(message);
    }

    public String chat(String userId, String systemPrompt, String message) {
        String sessionKey = userId + ":" + systemPrompt.hashCode();
        ChatSession session = sessions.computeIfAbsent(sessionKey, k -> new ChatSession(userId, systemPrompt));
        return session.chat(message);
    }

    public void clearMemory(String userId) {
        sessions.remove(userId);
        log.info("Cleared chat memory for user: {}", userId);
    }

    public void clearAllMemory() {
        sessions.clear();
        log.info("Cleared all chat memory");
    }

    public AiProperties getProperties() {
        return properties;
    }

    private static class ChatSession {
        private final String userId;
        private final String systemPrompt;

        public ChatSession(String userId) {
            this(userId, null);
        }

        public ChatSession(String userId, String systemPrompt) {
            this.userId = userId;
            this.systemPrompt = systemPrompt;
        }

        public String chat(String message) {
            log.warn("Chat functionality requires LangChain4j dependencies. " +
                    "Please add langchain4j dependencies to your project to enable AI features.");
            return "AI chat is not available. Please add LangChain4j dependencies to enable this feature.";
        }
    }
}
