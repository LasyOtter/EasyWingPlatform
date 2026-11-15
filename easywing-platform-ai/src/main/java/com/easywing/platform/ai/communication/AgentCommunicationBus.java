package com.easywing.platform.ai.communication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 间通信协议 - 支持 Agent 之间的消息传递和协作
 */
@Slf4j
@Service
public class AgentCommunicationBus {

    private final Map<String, AgentChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<AgentMessage>> messageSinks = new ConcurrentHashMap<>();
    private final List<AgentMessage> messageHistory = Collections.synchronizedList(new ArrayList<>());

    private static final int MAX_HISTORY = 10000;

    /**
     * 注册 Agent
     */
    public void registerAgent(String agentId, AgentInfo info) {
        AgentChannel channel = new AgentChannel(agentId, info);
        channels.put(agentId, channel);

        // 创建消息接收器
        Sinks.Many<AgentMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
        messageSinks.put(agentId, sink);

        log.info("Agent registered: {} - {}", agentId, info.getName());
    }

    /**
     * 注销 Agent
     */
    public void unregisterAgent(String agentId) {
        channels.remove(agentId);
        Sinks.Many<AgentMessage> sink = messageSinks.remove(agentId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        log.info("Agent unregistered: {}", agentId);
    }

    /**
     * 发送点对点消息
     */
    public Mono<MessageReceipt> sendMessage(AgentMessage message) {
        log.debug("Sending message from {} to {}: {}",
                message.getSenderId(), message.getReceiverId(), message.getType());

        // 验证接收者存在
        if (!channels.containsKey(message.getReceiverId())) {
            return Mono.error(new IllegalArgumentException(
                    "Receiver not found: " + message.getReceiverId()));
        }

        // 设置时间戳和ID
        message.setTimestamp(System.currentTimeMillis());
        message.setMessageId(UUID.randomUUID().toString());

        // 记录到历史
        addToHistory(message);

        // 发送消息
        Sinks.Many<AgentMessage> sink = messageSinks.get(message.getReceiverId());
        if (sink != null) {
            sink.tryEmitNext(message);
        }

        return Mono.just(MessageReceipt.builder()
                .messageId(message.getMessageId())
                .status(DeliveryStatus.DELIVERED)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 广播消息
     */
    public Mono<List<MessageReceipt>> broadcast(AgentMessage message) {
        log.debug("Broadcasting message from {}: {}", message.getSenderId(), message.getType());

        message.setTimestamp(System.currentTimeMillis());
        message.setMessageId(UUID.randomUUID().toString());
        message.setReceiverId("*"); // 广播标记

        addToHistory(message);

        return Flux.fromIterable(channels.keySet())
                .filter(agentId -> !agentId.equals(message.getSenderId())) // 不发给自己
                .flatMap(agentId -> {
                    AgentMessage copy = message.copy();
                    copy.setReceiverId(agentId);

                    Sinks.Many<AgentMessage> sink = messageSinks.get(agentId);
                    if (sink != null) {
                        sink.tryEmitNext(copy);
                    }

                    return Mono.just(MessageReceipt.builder()
                            .messageId(copy.getMessageId())
                            .receiverId(agentId)
                            .status(DeliveryStatus.DELIVERED)
                            .timestamp(System.currentTimeMillis())
                            .build());
                })
                .collectList();
    }

    /**
     * 订阅消息
     */
    public Flux<AgentMessage> subscribe(String agentId) {
        Sinks.Many<AgentMessage> sink = messageSinks.get(agentId);
        if (sink == null) {
            return Flux.error(new IllegalArgumentException("Agent not registered: " + agentId));
        }
        return sink.asFlux();
    }

    /**
     * 请求-响应模式
     */
    public Mono<AgentMessage> request(AgentMessage request, long timeoutMs) {
        String correlationId = UUID.randomUUID().toString();
        request.setCorrelationId(correlationId);
        request.setType(MessageType.REQUEST);

        // 订阅响应
        Mono<AgentMessage> responseMono = subscribe(request.getSenderId())
                .filter(msg -> correlationId.equals(msg.getCorrelationId()))
                .filter(msg -> msg.getType() == MessageType.RESPONSE)
                .next()
                .timeout(java.time.Duration.ofMillis(timeoutMs));

        // 发送请求
        return sendMessage(request)
                .then(responseMono);
    }

    /**
     * 发送响应
     */
    public Mono<MessageReceipt> respond(String requestId, String responderId, Object data) {
        // 从历史中查找原始请求
        AgentMessage originalRequest = messageHistory.stream()
                .filter(msg -> requestId.equals(msg.getMessageId()))
                .findFirst()
                .orElse(null);

        if (originalRequest == null) {
            return Mono.error(new IllegalArgumentException("Original request not found: " + requestId));
        }

        AgentMessage response = AgentMessage.builder()
                .senderId(responderId)
                .receiverId(originalRequest.getSenderId())
                .type(MessageType.RESPONSE)
                .correlationId(originalRequest.getCorrelationId())
                .data(data)
                .build();

        return sendMessage(response);
    }

    /**
     * 获取在线 Agent 列表
     */
    public List<AgentInfo> getOnlineAgents() {
        return channels.values().stream()
                .map(AgentChannel::getInfo)
                .toList();
    }

    /**
     * 获取 Agent 信息
     */
    public Optional<AgentInfo> getAgentInfo(String agentId) {
        AgentChannel channel = channels.get(agentId);
        return channel != null ? Optional.of(channel.getInfo()) : Optional.empty();
    }

    /**
     * 获取消息历史
     */
    public List<AgentMessage> getMessageHistory(String agentId, int limit) {
        synchronized (messageHistory) {
            return messageHistory.stream()
                    .filter(msg -> agentId.equals(msg.getSenderId()) || agentId.equals(msg.getReceiverId()))
                    .sorted(Comparator.comparingLong(AgentMessage::getTimestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    /**
     * 获取对话历史
     */
    public List<AgentMessage> getConversationHistory(String agentId1, String agentId2, int limit) {
        synchronized (messageHistory) {
            return messageHistory.stream()
                    .filter(msg ->
                            (agentId1.equals(msg.getSenderId()) && agentId2.equals(msg.getReceiverId())) ||
                            (agentId2.equals(msg.getSenderId()) && agentId1.equals(msg.getReceiverId()))
                    )
                    .sorted(Comparator.comparingLong(AgentMessage::getTimestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    /**
     * 添加到历史
     */
    private void addToHistory(AgentMessage message) {
        synchronized (messageHistory) {
            messageHistory.add(message);
            if (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(0);
            }
        }
    }

    /**
     * 清除历史
     */
    public void clearHistory() {
        synchronized (messageHistory) {
            messageHistory.clear();
        }
        log.info("Message history cleared");
    }

    // ========== 数据类 ==========

    @Data
    @AllArgsConstructor
    public static class AgentChannel {
        private String agentId;
        private AgentInfo info;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentInfo {
        private String id;
        private String name;
        private String type;
        private List<String> capabilities;
        private Map<String, Object> metadata;
        private long registeredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentMessage {
        private String messageId;
        private String senderId;
        private String receiverId;
        private MessageType type;
        private String correlationId; // 用于请求-响应关联
        private Object data;
        private Map<String, Object> metadata;
        private long timestamp;
        private MessagePriority priority;

        public AgentMessage copy() {
            return AgentMessage.builder()
                    .messageId(this.messageId)
                    .senderId(this.senderId)
                    .receiverId(this.receiverId)
                    .type(this.type)
                    .correlationId(this.correlationId)
                    .data(this.data)
                    .metadata(this.metadata != null ? new HashMap<>(this.metadata) : null)
                    .timestamp(this.timestamp)
                    .priority(this.priority)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReceipt {
        private String messageId;
        private String receiverId;
        private DeliveryStatus status;
        private long timestamp;
    }

    public enum MessageType {
        REQUEST,        // 请求
        RESPONSE,       // 响应
        NOTIFICATION,   // 通知
        COMMAND,        // 命令
        EVENT,          // 事件
        DATA            // 数据传输
    }

    public enum MessagePriority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum DeliveryStatus {
        PENDING,        // 待发送
        DELIVERED,      // 已送达
        FAILED,         // 失败
        TIMEOUT         // 超时
    }
}
