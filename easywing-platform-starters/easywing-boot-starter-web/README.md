# EasyWing Web Starter

Web 增强组件，提供全局异常处理、参数校验、统一的响应格式。

## 特性

- 全局异常处理
- 遵循 RFC 9457 Problem Details 规范
- 参数校验
- 统一响应格式

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 异常定义

```java
// 业务异常
throw new BizException("用户名已存在");

// 系统异常
throw new SystemException("系统错误");

// 校验异常
throw new ValidationException("用户名不能为空");
```

### 3. 参数校验

```java
@PostMapping("/user")
public void createUser(@Valid @RequestBody User user) {
    // 自动校验
}
```

## 响应格式

### 成功响应

```json
{
  "id": 1,
  "name": "张三"
}
```

### 错误响应（Problem Details）

```json
{
  "type": "https://easywing.io/errors/business",
  "title": "Business Error",
  "status": 400,
  "detail": "用户名已存在",
  "instance": "/api/users"
}
```

### 校验错误响应

```json
{
  "type": "https://easywing.io/errors/validation",
  "title": "Validation Error",
  "status": 400,
  "detail": "参数校验失败",
  "errors": {
    "name": "用户名不能为空",
    "email": "邮箱格式不正确"
  }
}
```

## 异常类型

| 异常 | HTTP 状态码 | 说明 |
|------|-------------|------|
| BizException | 400 | 业务异常 |
| ValidationException | 400 | 校验异常 |
| SystemException | 500 | 系统异常 |

## 自定义异常

```java
@EqualsAndHashCode(callSuper = true)
public class CustomException extends BizException {
    
    public CustomException(String message) {
        super(ErrorCode.BUSINESS_ERROR.getCode(), message);
    }
}
```
