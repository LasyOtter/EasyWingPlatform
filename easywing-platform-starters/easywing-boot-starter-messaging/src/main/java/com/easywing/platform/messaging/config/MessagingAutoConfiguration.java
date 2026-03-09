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
package com.easywing.platform.messaging.config;

import com.easywing.platform.messaging.annotation.DelayMessage;
import com.easywing.platform.messaging.converter.JsonMessageConverter;
import com.easywing.platform.messaging.converter.MessageConverter;
import com.easywing.platform.messaging.processor.MessageListenerBeanPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnProperty(prefix = "easywing.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessagingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MessagingAutoConfiguration.class);

    public MessagingAutoConfiguration() {
        log.info("EasyWing Messaging AutoConfiguration initialized");
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter messageConverter() {
        return new JsonMessageConverter();
    }

    @Bean
    public MessageListenerBeanPostProcessor messageListenerBeanPostProcessor(ApplicationContext applicationContext) {
        return new MessageListenerBeanPostProcessor(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingTemplate messagingTemplate(ApplicationContext applicationContext) {
        return new MessagingTemplate(applicationContext);
    }

    public static class MessagingTemplate {

        protected final ApplicationContext applicationContext;

        public MessagingTemplate(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        public <T> boolean send(String destination, T payload) {
            if (isSpringCloudStreamAvailable()) {
                return sendViaStreamBridge(destination, payload);
            }
            if (isRocketMQAvailable()) {
                return sendViaRocketMQ(destination, payload);
            }
            log.warn("No messaging infrastructure available, send operation skipped");
            return false;
        }

        public <T> boolean send(String destination, T payload, Map<String, Object> headers) {
            if (isSpringCloudStreamAvailable()) {
                return sendViaStreamBridge(destination, payload);
            }
            if (isRocketMQAvailable()) {
                return sendViaRocketMQ(destination, payload, headers);
            }
            log.warn("No messaging infrastructure available, send operation skipped");
            return false;
        }

        protected boolean isSpringCloudStreamAvailable() {
            try {
                Class.forName("org.springframework.cloud.stream.function.StreamBridge", false, 
                    getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        protected boolean isRocketMQAvailable() {
            try {
                Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate", false, 
                    getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        protected <T> boolean sendViaStreamBridge(String destination, T payload) {
            try {
                Class<?> streamBridgeClass = Class.forName("org.springframework.cloud.stream.function.StreamBridge");
                Object streamBridge = applicationContext.getBean(streamBridgeClass);
                java.lang.reflect.Method method = streamBridgeClass.getMethod("send", String.class, Object.class);
                return (boolean) method.invoke(streamBridge, destination, payload);
            } catch (Exception e) {
                log.error("Failed to send message via StreamBridge", e);
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        protected <T> boolean sendViaRocketMQ(String destination, T payload) {
            return sendViaRocketMQ(destination, payload, new HashMap<>());
        }

        @SuppressWarnings("unchecked")
        protected <T> boolean sendViaRocketMQ(String destination, T payload, Map<String, Object> headers) {
            try {
                Class<?> rocketMQTemplateClass = Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate");
                Object rocketMQTemplate = applicationContext.getBean(rocketMQTemplateClass);
                
                Object message = createRocketMessage(payload, headers);
                java.lang.reflect.Method method = rocketMQTemplateClass.getMethod("send", String.class, message.getClass());
                Object result = method.invoke(rocketMQTemplate, destination, message);
                
                return result != null;
            } catch (Exception e) {
                log.error("Failed to send message via RocketMQ", e);
                return false;
            }
        }

        protected <T> Object createRocketMessage(T payload, Map<String, Object> headers) throws Exception {
            Class<?> builderClass = Class.forName("org.springframework.messaging.support.GenericMessageBuilder");
            java.lang.reflect.Method createPayload = builderClass.getMethod("createPayload", Object.class);
            Object builder = createPayload.invoke(null, payload);
            
            java.lang.reflect.Method copyHeaders = builderClass.getMethod("copyHeaders", Map.class);
            copyHeaders.invoke(builder, headers);
            
            java.lang.reflect.Method build = builderClass.getMethod("build");
            return build.invoke(builder);
        }
    }

    public static class RocketMQMessagingTemplate extends MessagingTemplate {

        public RocketMQMessagingTemplate(ApplicationContext applicationContext) {
            super(applicationContext);
        }

        public <T> boolean sendInTransaction(String destination, T payload, Runnable businessLogic) {
            if (!isRocketMQAvailable()) {
                log.warn("RocketMQ not available, transaction send skipped");
                return false;
            }
            return sendInTransactionViaRocketMQ(destination, payload, businessLogic);
        }

        public <T> boolean sendDelay(String destination, T payload, int delayLevel) {
            if (!isRocketMQAvailable()) {
                log.warn("RocketMQ not available, delay send skipped");
                return false;
            }
            return sendDelayViaRocketMQ(destination, payload, delayLevel);
        }

        public <T> boolean sendDelay(String destination, T payload, long delayMillis) {
            int level = convertDelayToLevel(delayMillis);
            return sendDelay(destination, payload, level);
        }

        @SuppressWarnings("unchecked")
        private <T> boolean sendInTransactionViaRocketMQ(String destination, T payload, Runnable businessLogic) {
            try {
                Class<?> rocketMQTemplateClass = Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate");
                Object rocketMQTemplate = applicationContext.getBean(rocketMQTemplateClass);
                
                businessLogic.run();
                
                Object message = createRocketMessage(payload, new HashMap<>());
                java.lang.reflect.Method method = rocketMQTemplateClass.getMethod("sendMessageInTransaction", String.class, message.getClass(), Object.class);
                Object result = method.invoke(rocketMQTemplate, destination, message, null);
                
                return result != null;
            } catch (Exception e) {
                log.error("Failed to send transaction message via RocketMQ", e);
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        private <T> boolean sendDelayViaRocketMQ(String destination, T payload, int delayLevel) {
            try {
                Class<?> rocketMQTemplateClass = Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate");
                Object rocketMQTemplate = applicationContext.getBean(rocketMQTemplateClass);
                
                Map<String, Object> headers = new HashMap<>();
                headers.put("DELAY", delayLevel);
                
                Object message = createRocketMessage(payload, headers);
                java.lang.reflect.Method method = rocketMQTemplateClass.getMethod("syncSend", String.class, message.getClass(), long.class, int.class);
                Object result = method.invoke(rocketMQTemplate, destination, message, 3000L, delayLevel);
                
                return result != null;
            } catch (Exception e) {
                log.error("Failed to send delay message via RocketMQ", e);
                return false;
            }
        }

        private int convertDelayToLevel(long delayMillis) {
            if (delayMillis <= 1000) return DelayMessage.DelayLevel.LEVEL_1S;
            if (delayMillis <= 5000) return DelayMessage.DelayLevel.LEVEL_5S;
            if (delayMillis <= 10000) return DelayMessage.DelayLevel.LEVEL_10S;
            if (delayMillis <= 30000) return DelayMessage.DelayLevel.LEVEL_30S;
            if (delayMillis <= 60000) return DelayMessage.DelayLevel.LEVEL_1M;
            if (delayMillis <= 120000) return DelayMessage.DelayLevel.LEVEL_2M;
            if (delayMillis <= 180000) return DelayMessage.DelayLevel.LEVEL_3M;
            if (delayMillis <= 300000) return DelayMessage.DelayLevel.LEVEL_5M;
            if (delayMillis <= 600000) return DelayMessage.DelayLevel.LEVEL_10M;
            return DelayMessage.DelayLevel.LEVEL_30M;
        }
    }
}
