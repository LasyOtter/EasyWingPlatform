# EasyWing Resilience4j Starter

熔断、限流、降级组件，基于 Resilience4j，提供高可用保护。

## 特性

- 熔断器（Circuit Breaker）
- 限流器（Rate Limiter）
- 重试（Retry）
- 降级（Fallback）
- 隔离（Bulkhead）

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-resilience4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      userService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  ratelimiter:
    instances:
      api:
        limit-for-period: 100
        limit-refresh-period: 1s
  retry:
    instances:
      userService:
        max-attempts: 3
        wait-duration: 500ms
```

## 使用

### 熔断器

```java
@Service
public class UserService {

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
    public User getUser(Long id) {
        return userClient.getUser(id);
    }

    private User getUserFallback(Long id, Throwable t) {
        return User.builder()
            .id(id)
            .name("默认用户")
            .build();
    }
}
```

### 限流

```java
@Service
public class OrderService {

    @RateLimiter(name = "api", fallbackMethod = "createOrderFallback")
    public Order createOrder(Order order) {
        return orderClient.create(order);
    }

    private Order createOrderFallback(Order order, Throwable t) {
        throw new BizException("系统繁忙，请稍后重试");
    }
}
```

### 重试

```java
@Service
public class PaymentService {

    @Retry(name = "paymentService", maxAttempts = 3)
    public void pay(Order order) {
        paymentGateway.pay(order);
    }
}
```

### 隔离

```java
@Service
public class NotificationService {

    @Bulkhead(name = "notification", fallbackMethod = "sendFallback")
    public void send(Notification notification) {
        // 发送通知
    }
}
```

## 监控

结合 Spring Boot Actuator：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,circuitbreakers,ratelimiters
  endpoint:
    health:
      show-details: always
```

```bash
# 查看熔断器状态
curl http://localhost:8080/actuator/circuitbreakers

# 查看限流状态
curl http://localhost:8080/actuator/ratelimiters
```
