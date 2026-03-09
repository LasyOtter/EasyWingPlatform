# EasyWing OTEL Starter

分布式链路追踪组件，基于 OpenTelemetry，支持 SkyWalking、Zipkin、Jaeger。

## 特性

- 自动埋点
- 链路追踪
- 性能监控
- 多后端支持

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-otel</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

#### SkyWalking

```yaml
easywing:
  otel:
    enabled: true
    exporter:
      type: skywalking
    skywalking:
      service-name: order-service
      collector地址: localhost:11800
```

#### Jaeger

```yaml
easywing:
  otel:
    enabled: true
    exporter:
      type: jaeger
    jaeger:
      endpoint: http://localhost:14268
      service-name: order-service
```

#### Zipkin

```yaml
easywing:
  otel:
    enabled: true
    exporter:
      type: zipkin
    zipkin:
      endpoint: http://localhost:9411
```

## 自动埋点

### HTTP

```java
@RestController
public class OrderController {

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        // 自动记录 Span
        return orderService.getOrder(id);
    }
}
```

### Feign

```java
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{id}")
    User getUser(@PathVariable Long id);
}
```

### 数据库

自动追踪 SQL 执行：

```java
@Service
public class OrderService {

    public List<Order> list() {
        // 自动记录 SQL 执行时间
        return orderMapper.selectList(null);
    }
}
```

## 手动埋点

```java
@Autowired
private Tracer tracer;

public void process() {
    Span span = tracer.spanBuilder("custom-operation")
        .startSpan();
    
    try {
        // 业务逻辑
    } finally {
        span.end();
    }
}
```

## 传播上下文

跨服务传递 TraceId：

```java
// 发送方
Template template;
template.send("topic", payload, Map.of("traceparent", tracer.currentSpan().getContext()));

// 接收方
@MessageListener
public void receive(Message msg) {
    String traceparent = msg.getHeaders().get("traceparent");
    // 自动关联
}
```
