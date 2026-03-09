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
package com.easywing.platform.messaging.processor;

import com.easywing.platform.messaging.annotation.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MessageListenerBeanPostProcessor implements BeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(MessageListenerBeanPostProcessor.class);

    private final ApplicationContext applicationContext;
    private final Map<String, ListenerMethodInfo> registeredListeners = new HashMap<>();
    private boolean processed = false;

    public MessageListenerBeanPostProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (processed) {
            return bean;
        }

        Class<?> targetClass = ClassUtils.getUserClass(bean);
        for (Method method : targetClass.getDeclaredMethods()) {
            MessageListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, MessageListener.class);
            if (annotation != null) {
                processListenerMethod(bean, method, annotation, beanName);
            }
        }

        for (Method method : targetClass.getMethods()) {
            MessageListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, MessageListener.class);
            if (annotation != null && !method.isSynthetic()) {
                processListenerMethod(bean, method, annotation, beanName);
            }
        }

        return bean;
    }

    private void processListenerMethod(Object bean, Method method, MessageListener annotation, String beanName) {
        String destination = annotation.destination();
        if (destination.isEmpty()) {
            destination = beanName + "-" + method.getName();
        }

        Class<?> payloadType = extractPayloadType(method);
        if (payloadType == null) {
            payloadType = Object.class;
        }

        ListenerMethodInfo info = new ListenerMethodInfo(bean, method, payloadType, annotation);
        registeredListeners.put(destination, info);

        log.info("Found @MessageListener: {}.{} -> destination={}", 
            bean.getClass().getSimpleName(), method.getName(), destination);
    }

    private Class<?> extractPayloadType(Method method) {
        if (method.getParameterCount() > 0) {
            for (int i = 0; i < method.getParameterCount(); i++) {
                Class<?> paramType = method.getParameterTypes()[i];
                if (!Message.class.isAssignableFrom(paramType) && 
                    !paramType.getName().startsWith("org.springframework.messaging")) {
                    Type genericType = method.getGenericParameterTypes()[i];
                    if (genericType instanceof ParameterizedType pt) {
                        Type[] typeArgs = pt.getActualTypeArguments();
                        if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> typeArg) {
                            return typeArg;
                        }
                    }
                    return paramType;
                }
            }
        }
        return Object.class;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        if (processed) {
            return;
        }
        processed = true;

        if (!isSpringCloudStreamAvailable()) {
            log.warn("Spring Cloud Stream not available, @MessageListener registration skipped. " +
                "Listeners found: {}", registeredListeners.size());
            return;
        }

        try {
            registerListenersWithSpringCloudStream();
        } catch (Exception e) {
            log.error("Failed to register @MessageListener methods", e);
        }

        log.info("Registered {} @MessageListener methods with Spring Cloud Stream", registeredListeners.size());
    }

    private boolean isSpringCloudStreamAvailable() {
        try {
            Class.forName("org.springframework.cloud.stream.function.StreamBridge", false, 
                getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void registerListenersWithSpringCloudStream() throws Exception {
        Class<?> streamBridgeClass = Class.forName("org.springframework.cloud.stream.function.StreamBridge");
        
        Object streamBridge = applicationContext.getBean(streamBridgeClass);
        
        for (Map.Entry<String, ListenerMethodInfo> entry : registeredListeners.entrySet()) {
            String destination = entry.getKey();
            ListenerMethodInfo info = entry.getValue();
            registerConsumer(streamBridge, destination, info);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerConsumer(Object streamBridge, String destination, ListenerMethodInfo info) throws Exception {
        Object bean = info.bean;
        Method method = info.method;
        Class<?> payloadType = info.payloadType;

        org.springframework.util.function.ThrowingConsumer<?> consumer = (msg) -> {
            try {
                Object payload = msg;
                if (msg instanceof Message) {
                    payload = ((Message<?>) msg).getPayload();
                }

                Object[] args = buildMethodArguments(method, payload);
                method.invoke(bean, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking message listener", e);
            }
        };

        Class<?> functionRegistrationClass = Class.forName("org.springframework.cloud.function.context.FunctionRegistration");
        Class<?> functionRegistryClass = Class.forName("org.springframework.cloud.function.context.FunctionRegistry");
        
        Object functionRegistry = applicationContext.getBeanProvider(functionRegistryClass).getIfAvailable();
        if (functionRegistry == null) {
            log.warn("FunctionRegistry not available");
            return;
        }

        Object registration = functionRegistrationClass.getConstructor(Object.class, String.class)
            .newInstance(consumer, destination);

        java.lang.reflect.Method typeMethod = functionRegistrationClass.getMethod("type", java.util.function.Supplier.class);
        typeMethod.invoke(registration, (java.util.function.Supplier<?>) () -> payloadType);

        java.lang.reflect.Method registerMethod = functionRegistryClass.getMethod("register", functionRegistrationClass);
        registerMethod.invoke(functionRegistry, registration);

        log.debug("Registered consumer for destination: {} with payload type: {}", destination, payloadType.getName());
    }

    private Object[] buildMethodArguments(Method method, Object payload) {
        java.util.List<Object> args = new java.util.ArrayList<>();
        
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> paramType = method.getParameterTypes()[i];
            
            if (paramType.isInstance(payload)) {
                args.add(payload);
            } else if (Message.class.isAssignableFrom(paramType)) {
                args.add(payload);
            } else if (paramType.equals(Object.class)) {
                args.add(payload);
            } else {
                args.add(null);
            }
        }
        
        return args.toArray();
    }

    public Map<String, ListenerMethodInfo> getRegisteredListeners() {
        return registeredListeners;
    }

    public static class ListenerMethodInfo {
        private final Object bean;
        private final Method method;
        private final Class<?> payloadType;
        private final MessageListener annotation;

        public ListenerMethodInfo(Object bean, Method method, Class<?> payloadType, MessageListener annotation) {
            this.bean = bean;
            this.method = method;
            this.payloadType = payloadType;
            this.annotation = annotation;
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public Class<?> getPayloadType() { return payloadType; }
        public MessageListener getAnnotation() { return annotation; }
    }
}
