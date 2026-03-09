# 需求文档

## 简介

异步消息驱动 Starter（async-messaging-starter）为 Spring Boot 微服务提供统一的消息中间件集成能力。该系统支持 Kafka 和 RabbitMQ，通过抽象层实现消息发送、接收、转换和拦截功能，简化异步消息驱动架构的开发。

## 术语表

- **MessageTemplate**: 消息发送模板，提供统一的消息发送接口
- **MessageListener**: 消息监听器，处理接收到的消息
- **MessageConverter**: 消息转换器，负责消息的序列化和反序列化
- **MessageInterceptor**: 消息拦截器，在消息发送和接收前后执行自定义逻辑
- **MessagingAdapter**: 消息中间件适配器，封装与具体消息中间件的交互
- **SendResult**: 发送结果，包含消息发送的状态和元数据
- **MessageContext**: 消息上下文，包含消息的元数据和处理信息
- **Destination**: 消息目标，可以是主题（Topic）或队列（Queue）

## 需求

### 需求 1: 同步消息发送

**用户故事:** 作为开发者，我希望能够同步发送消息到指定目标，以便在消息发送完成后继续执行后续逻辑。

#### 验收标准

1. WHEN 调用 MessageTemplate 的 send 方法时，THE MessageTemplate SHALL 将消息发送到指定的 destination
2. WHEN 消息发送成功时，THE MessageTemplate SHALL 返回包含发送状态和元数据的 SendResult
3. WHEN 消息发送失败时，THE MessageTemplate SHALL 抛出 MessagingException
4. WHERE 提供了分区键（partition key），THE MessageTemplate SHALL 使用该键确定消息的分区

### 需求 2: 异步消息发送

**用户故事:** 作为开发者，我希望能够异步发送消息，以便不阻塞当前线程并提高系统吞吐量。

#### 验收标准

1. WHEN 调用 MessageTemplate 的 sendAsync 方法时，THE MessageTemplate SHALL 立即返回 CompletableFuture
2. WHEN 异步发送完成时，THE CompletableFuture SHALL 包含 SendResult
3. WHERE 提供了 SendCallback，WHEN 发送成功时，THE MessageTemplate SHALL 调用 callback 的 onSuccess 方法
4. WHERE 提供了 SendCallback，WHEN 发送失败时，THE MessageTemplate SHALL 调用 callback 的 onFailure 方法

### 需求 3: 事务消息发送

**用户故事:** 作为开发者，我希望能够在事务中发送消息，以便确保消息发送与业务操作的原子性。

#### 验收标准

1. WHEN 调用 sendInTransaction 方法时，THE MessageTemplate SHALL 在事务执行器完成后发送消息
2. WHEN 事务执行器执行失败时，THE MessageTemplate SHALL 回滚消息发送
3. WHEN 事务执行器执行成功但消息发送失败时，THE MessageTemplate SHALL 抛出 MessagingException

### 需求 4: 消息监听

**用户故事:** 作为开发者，我希望能够注册消息监听器来接收和处理消息，以便实现事件驱动的业务逻辑。

#### 验收标准

1. WHEN 消息到达时，THE MessagingAdapter SHALL 调用已注册的 MessageListener 的 onMessage 方法
2. WHEN 调用 onMessage 方法时，THE MessagingAdapter SHALL 提供消息内容和 MessageContext
3. WHEN MessageListener 处理成功时，THE MessagingAdapter SHALL 确认消息消费
4. WHEN MessageListener 抛出异常时，THE MessagingAdapter SHALL 根据配置进行重试或将消息发送到死信队列

### 需求 5: 消息序列化和反序列化

**用户故事:** 作为开发者，我希望系统能够自动处理消息的序列化和反序列化，以便专注于业务逻辑而不是数据转换。

#### 验收标准

1. WHEN 发送消息时，THE MessageConverter SHALL 将消息对象转换为字节数组
2. WHEN 接收消息时，THE MessageConverter SHALL 将字节数组转换为目标类型的对象
3. WHEN 转换失败时，THE MessageConverter SHALL 抛出 ConversionException
4. THE MessageConverter SHALL 支持判断是否能够转换指定类型

### 需求 6: 消息拦截

**用户故事:** 作为开发者，我希望能够在消息发送和接收的关键点插入自定义逻辑，以便实现追踪、监控和消息增强等功能。

#### 验收标准

1. WHEN 发送消息前，THE MessageTemplate SHALL 按顺序调用所有 MessageInterceptor 的 preSend 方法
2. WHEN 发送消息后，THE MessageTemplate SHALL 按顺序调用所有 MessageInterceptor 的 postSend 方法
3. WHEN 接收消息前，THE MessageListener SHALL 按顺序调用所有 MessageInterceptor 的 preReceive 方法
4. WHEN 接收消息后，THE MessageListener SHALL 按顺序调用所有 MessageInterceptor 的 postReceive 方法
5. THE MessageInterceptor SHALL 按照 getOrder 方法返回的顺序值执行，数值越小越先执行

### 需求 7: Kafka 适配器

**用户故事:** 作为开发者，我希望能够使用 Kafka 作为消息中间件，以便利用 Kafka 的高吞吐量和持久化特性。

#### 验收标准

1. THE KafkaMessagingAdapter SHALL 实现 MessagingAdapter 接口
2. WHEN 发送消息时，THE KafkaMessagingAdapter SHALL 使用 KafkaTemplate 发送消息到指定 topic
3. WHERE 消息包含分区键，THE KafkaMessagingAdapter SHALL 使用该键确定消息分区
4. WHERE 消息包含自定义头信息，THE KafkaMessagingAdapter SHALL 将头信息添加到 Kafka 消息头
5. WHEN 调用 getAdapterType 方法时，THE KafkaMessagingAdapter SHALL 返回 "kafka"

### 需求 8: RabbitMQ 适配器

**用户故事:** 作为开发者，我希望能够使用 RabbitMQ 作为消息中间件，以便利用 RabbitMQ 的灵活路由和消息确认机制。

#### 验收标准

1. THE RabbitMQMessagingAdapter SHALL 实现 MessagingAdapter 接口
2. WHEN destination 格式为 "exchange/routingKey" 时，THE RabbitMQMessagingAdapter SHALL 解析并使用指定的 exchange 和 routingKey
3. WHEN destination 不包含 "/" 时，THE RabbitMQMessagingAdapter SHALL 使用默认 exchange 和 destination 作为 routingKey
4. WHEN 异步发送消息时，THE RabbitMQMessagingAdapter SHALL 使用 CorrelationData 实现发送确认回调
5. WHEN 调用 getAdapterType 方法时，THE RabbitMQMessagingAdapter SHALL 返回 "rabbitmq"

### 需求 9: 消息包装和元数据

**用户故事:** 作为开发者，我希望系统能够自动为消息添加元数据，以便实现消息追踪和上下文传递。

#### 验收标准

1. WHEN 发送消息时，THE MessageTemplate SHALL 将业务对象包装为 Message 对象
2. THE Message SHALL 包含唯一的消息 ID
3. THE Message SHALL 包含消息头（headers），用于存储元数据
4. THE Message SHALL 包含消息负载（payload），即业务对象

### 需求 10: 错误处理

**用户故事:** 作为开发者，我希望系统能够妥善处理消息发送和接收过程中的错误，以便提高系统的可靠性。

#### 验收标准

1. WHEN 消息发送失败时，THE MessageTemplate SHALL 抛出包含详细错误信息的 MessagingException
2. WHEN 消息转换失败时，THE MessageConverter SHALL 抛出 ConversionException
3. WHEN 适配器操作失败时，THE MessagingAdapter SHALL 将底层异常包装为 MessagingException
4. THE MessagingException SHALL 保留原始异常作为 cause，以便问题诊断
