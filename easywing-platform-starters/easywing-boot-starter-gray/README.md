# EasyWing Gray Starter

灰度发布组件，支持基于 Header、Cookie、参数、权重的灰度路由。

## 特性

- 多维度灰度规则
- 流量染色
- 灰度路由
- 规则动态配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-gray</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
easywing:
  gray:
    enabled: true
    rules:
      - name: version-rule
        version: v2
        weight: 30
      - name: user-rule
        users: 1001,1002,1003
      - name: header-rule
        header: X-Gray-Version
        values: v2
```

### 3. 使用

```java
@Service
public class OrderService {

    @Gray(version = "v2")
    public Order getOrderV2(Long id) {
        return orderMapper.selectById(id);
    }
}
```

## 灰度策略

### 版本灰度

```yaml
rules:
  - name: version-rule
    type: version
    version: v2
    weight: 30  # 30% 流量
```

### 用户灰度

```yaml
rules:
  - name: user-rule
    type: user
    users: 1001,1002,1003  # 指定用户
```

### Header 灰度

```yaml
rules:
  - name: header-rule
    type: header
    header: X-Gray-Version
    values: v2
```

### IP 灰度

```yaml
rules:
  - name: ip-rule
    type: ip
    ips: 192.168.1.1,192.168.1.2
```

## 灰度路由

```java
@Configuration
public class GrayConfig {

    @Bean
    public GrayFilter grayFilter() {
        return new GrayFilter();
    }
}
```

### Nginx 配置

```nginx
location / {
    # 传递灰度版本
    proxy_set_header X-Gray-Version $http_x_gray_version;
    proxy_pass http://backend;
}
```

## 动态规则

通过配置中心动态修改灰度规则：

```java
@Service
public class GrayRuleService {

    @Autowired
    private GrayProperties properties;

    public void updateRule(GrayRule rule) {
        properties.getRules().add(rule);
    }
}
```
