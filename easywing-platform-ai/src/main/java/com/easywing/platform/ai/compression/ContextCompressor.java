package com.easywing.platform.ai.compression;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ContextCompressor {

    private final AiProperties properties;

    public ContextCompressor(AiProperties properties) {
        this.properties = properties;
    }

    public ConversationContext compress(ConversationContext context) {
        if (!properties.getCompression().isEnabled()) {
            return context;
        }

        int targetTokens = properties.getCompression().getTargetTokens();
        if (context.getMessages().size() <= 10) {
            return context;
        }

        log.info("Compressing context from {} messages", context.getMessages().size());

        List<ChatMessage> originalMessages = context.getMessages();
        List<ChatMessage> compressedMessages = new ArrayList<>();

        ChatMessage systemMessage = originalMessages.stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst()
                .orElse(null);

        if (systemMessage != null) {
            compressedMessages.add(systemMessage);
        }

        String summary = generateSummary(context);
        ChatMessage summaryMessage = ChatMessage.builder()
                .role("system")
                .content("[Previous conversation summarized: " + summary + "]")
                .build();
        compressedMessages.add(summaryMessage);

        int preservedCount = originalMessages.size() / 2;
        List<ChatMessage> recentMessages = originalMessages.stream()
                .skip(Math.max(0, originalMessages.size() - preservedCount))
                .toList();
        compressedMessages.addAll(recentMessages);

        context.setMessages(compressedMessages);
        context.setMessageCount(compressedMessages.size());
        context.setSummary(summary);

        return context;
    }

    public String generateSummary(ConversationContext context) {
        int userCount = 0;
        int assistantCount = 0;

        for (ChatMessage msg : context.getMessages()) {
            if ("user".equals(msg.getRole())) userCount++;
            else if ("assistant".equals(msg.getRole())) assistantCount++;
        }

        return String.format("Conversation with %d user messages and %d assistant responses",
                userCount, assistantCount);
    }
}