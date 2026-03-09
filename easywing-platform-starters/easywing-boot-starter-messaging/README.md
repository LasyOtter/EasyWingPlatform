# EasyWing Messaging Starter

基于 Spring Cloud Stream 的统一消息驱动组件，支持 Kafka、RabbitMQ、RocketMQ。

## 特性

- 统一的消息发送 API
- 注解驱动的消息监听
- 支持事务消息（RocketMQ）
- 支持延迟消息（RocketMQ）
- 内置链路追踪拦截器
- 内置 Metrics 拦截器

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-messaging</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

#### Kafka 配置

```yaml
spring:
  cloud:
    stream:
      bindings:
        order-topic:
          destination: order-topic
          group: order-group
      binders:
        kafka:
          type: kafka
          environment:
            spring.kafka.bootstrap-servers: localhost:9092
```

#### RabbitMQ 配置

```yaml
spring:
  cloud:
    stream:
      bindings:
        order-topic:
          destination: order-topic
      binders:
        rabbitmq:
          type: rabbitmq
          environment:
            spring.rabbitmq.host: localhost
            spring.rabbitmq.port: 5672
```

#### RocketMQ 配置

```yaml
rocketmq:
  producer:
    group: order-producer-group
    transactionMQ: order-transaction
```

### 3. 发送消息

```java
@Service
public class OrderService {

    @Autowired
    private MessagingTemplate template;

    public void createOrder(Order order) {
        template.send("order-topic", order);
    }
}
```

### 4. 接收消息

```java
@Service
public class OrderListener {

    @MessageListener(destination = "order-topic")
    public void handleOrder(Order order) {
        System.out.println("Received order: " + order);
    }
}
```

## 高级特性

### 事务消息（仅 RocketMQ）

```java
@Service
public class OrderService {

    @Autowired
    private RocketMQMessagingTemplate template;

    public void createOrderInTransaction(Order order) {
        template.sendInTransaction("order-topic", order, () -> {
            // 业务逻辑
            orderRepository.save(order);
        });
    }
}
```

### 延迟消息（仅 RocketMQ）

```java
@Service
public class NotificationService {

    @Autowired
    private RocketMQMessagingTemplate template;

    public void sendDelayNotification(User user) {
        // 延迟 10 秒
        template.sendDelay("notify-topic", user, 10000);
        
        // 或使用预设级别
        template.sendDelay("notify-topic", user, DelayMessage.DelayLevel.LEVEL_10S);
    }
}
```

延迟级别：
- `LEVEL_1S` - 1秒
- `LEVEL_5S` - 5秒
- `LEVEL_10S` - 10秒
- `LEVEL_30S` - 30秒
- `LEVEL_1M` - 1分钟
- `LEVEL_5M` - 5分钟
- ...

### 消息头

```java
template.send("topic", payload, Map.of("headerKey", "headerValue"));
```

## 核心类

| 类 | 说明 |
|---|---|
| `MessagingTemplate` | 消息发送模板 |
| `@MessageListener` | 消息监听注解 |
| `@DelayMessage` | 延迟消息注解 |
| `Message` | 消息对象 |
| `MessageHeaders` | 消息头 |

## 切换消息中间件

只需修改配置和依赖，无需修改业务代码：

```yaml
# Kafka
spring.cloud.stream.binders.kafka.type: kafka

# RabbitMQ  
spring.cloud.stream.binders.rabbitmq.type: rabbitmq
```
