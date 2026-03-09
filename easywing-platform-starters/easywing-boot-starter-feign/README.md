# EasyWing Feign Starter

增强型 Feign 客户端，支持 Header 自动传递、请求/响应日志、请求重试。

## 特性

- Header 自动传播
- 请求/响应日志
- 统一的错误处理
- 请求超时配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-feign</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
easywing:
  feign:
    enabled: true
    client:
      connect-timeout: 5000
      read-timeout: 10000
    logging:
      enabled: true
      level: basic
```

### 3. 定义 Feign 客户端

```java
@FeignClient(name = "user-service", url = "${services.user.url}")
public interface UserClient {

    @GetMapping("/users/{id}")
    User getUser(@PathVariable("id") Long id);

    @PostMapping("/users")
    User createUser(@RequestBody User user);
}
```

## Header 传播

自动将当前请求的 Header 传递到下游服务：

```java
@FeignClient(name = "user-service")
public interface UserClient {

    // 认证信息会自动传递
    @GetMapping("/users/current")
    User getCurrentUser();
}
```

### 自定义 Header

```java
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{id}")
    @Headers({"X-Request-Id: #{T(java.util.UUID).randomUUID()}"})
    User getUser(@PathVariable("id") Long id);
}
```

## 请求日志

配置日志级别：

```yaml
logging:
  level:
    com.easywing.platform.feign: DEBUG
```

## 熔断与重试

结合 Resilience4j 使用：

```java
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{id}")
    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    User getUser(@PathVariable("id") Long id);

    default User getUserFallback(Long id) {
        return new User();
    }
}
```

## 继承父类 Header

```java
@Component
public class AuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 获取当前请求的认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            template.header("Authorization", auth.getCredentials().toString());
        }
    }
}
```
