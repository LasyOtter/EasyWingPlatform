# EasyWing Virtual Thread Starter

虚拟线程支持，提升高并发场景下的系统吞吐量和资源利用率。

## 特性

- 自动配置虚拟线程
- 支持 Tomcat、Undertow、Jetty
- 支持 Spring MVC、Spring WebFlux
- 任务调度虚拟线程化

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-virtual-thread</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
easywing:
  virtual-thread:
    enabled: true
    tomcat:
      enabled: true
    executor:
      enabled: true
      core-size: 100
      max-size: 200
```

### 3. 使用

只需 JDK 21 + 即可自动启用：

```java
@RestController
public class OrderController {

    @GetMapping("/orders")
    public List<Order> list() {
        // 虚拟线程自动启用
        return orderService.list();
    }
}
```

## 工作原理

### Tomcat 虚拟线程

```
请求 → 虚拟线程 → Controller
           ↓
        业务逻辑
           ↓
        释放线程
```

### 任务执行器

```java
@Service
public class OrderService {

    @Autowired
    private TaskExecutor taskExecutor;

    public void process() {
        // 使用虚拟线程执行器
        taskExecutor.execute(() -> {
            // 任务逻辑
        });
    }
}
```

## 注意事项

1. 虚拟线程不支持 ThreadLocal，推荐使用 `ScopedValue`
2. 避免在虚拟线程中使用阻塞操作
3. 确保依赖库兼容虚拟线程
