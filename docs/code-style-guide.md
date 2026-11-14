# 🎨 代码风格统一方案

> 更新时间: 2026-11-14

---

## 📋 当前状况分析

### 发现的不一致问题

1. **依赖注入方式不统一**
   - 部分类使用 `@RequiredArgsConstructor` (Lombok)
   - 部分类使用构造函数注入
   - 少数类使用 `@Autowired` 字段注入

2. **日志格式不一致**
   - 占位符使用混乱（`{}` vs `%s`）
   - 日志级别使用不规范
   - 异常日志格式不统一

3. **命名约定不统一**
   - 常量命名（部分使用下划线，部分驼峰）
   - 方法命名（部分使用缩写）
   - 变量命名（部分过于简短）

---

## 🎯 统一标准

### 1. 依赖注入标准

#### ✅ 推荐：使用 @RequiredArgsConstructor

```java
@Slf4j
@Service
@RequiredArgsConstructor  // ✅ 推荐
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    
    // Lombok 自动生成构造函数
}
```

**优点**:
- 代码简洁
- 自动生成构造函数
- 字段自动 final
- IDE 支持良好

#### ❌ 避免：字段注入

```java
@Service
public class UserService {
    
    @Autowired  // ❌ 不推荐
    private UserRepository userRepository;
    
    @Autowired  // ❌ 不推荐
    private PasswordEncoder passwordEncoder;
}
```

**缺点**:
- 测试困难
- 无法使用 final
- 循环依赖难以发现
- 不利于重构

#### ⚠️ 备选：显式构造函数

```java
@Slf4j
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // 只有在需要自定义逻辑时才手写
    public UserService(UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        // 自定义初始化逻辑
        log.info("UserService initialized");
    }
}
```

---

### 2. 日志格式标准

#### 日志级别使用规范

```java
@Slf4j
public class Example {
    
    public void process() {
        // ✅ TRACE: 详细的执行流程（开发调试）
        log.trace("Entering process method with params: {}", params);
        
        // ✅ DEBUG: 调试信息（开发环境）
        log.debug("Processing user data: userId={}, operation={}", userId, operation);
        
        // ✅ INFO: 关键业务流程（生产环境正常输出）
        log.info("User registered successfully: userId={}, username={}", user.getId(), user.getUsername());
        
        // ✅ WARN: 警告（可恢复的异常）
        log.warn("Cache miss for key: {}, loading from database", cacheKey);
        
        // ✅ ERROR: 错误（需要人工介入）
        log.error("Failed to process payment: orderId={}, amount={}", orderId, amount, exception);
    }
}
```

#### 日志格式规范

```java
// ✅ 推荐：使用占位符
log.info("User {} logged in from IP {}", username, ipAddress);
log.error("Failed to process order {}", orderId, exception);

// ❌ 避免：字符串拼接
log.info("User " + username + " logged in from IP " + ipAddress);

// ❌ 避免：String.format（性能差）
log.info(String.format("User %s logged in from IP %s", username, ipAddress));

// ✅ 结构化日志（关键业务）
log.info("Business operation completed: operation={}, userId={}, duration={}ms, result={}",
    operation, userId, duration, result);

// ✅ 异常日志（包含堆栈）
log.error("Unexpected error occurred: operation={}, userId={}", operation, userId, exception);

// ⚠️ 敏感信息脱敏
log.info("User login attempt: username={}, ip={}", 
    maskUsername(username), ipAddress);  // 脱敏用户名
```

#### 日志上下文规范

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    public void processOrder(Order order) {
        // ✅ 关键操作记录日志
        log.info("Processing order: orderId={}, userId={}, amount={}", 
            order.getId(), order.getUserId(), order.getAmount());
        
        try {
            validateOrder(order);
            calculatePrice(order);
            saveOrder(order);
            
            // ✅ 成功日志
            log.info("Order processed successfully: orderId={}, totalAmount={}", 
                order.getId(), order.getTotalAmount());
                
        } catch (ValidationException e) {
            // ✅ 业务异常（WARN 级别）
            log.warn("Order validation failed: orderId={}, reason={}", 
                order.getId(), e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // ✅ 系统异常（ERROR 级别，包含堆栈）
            log.error("Unexpected error processing order: orderId={}", 
                order.getId(), e);
            throw new SystemException("Order processing failed", e);
        }
    }
}
```

---

### 3. 命名约定标准

#### 常量命名

```java
public class Constants {
    
    // ✅ 公共常量：全大写，下划线分隔
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final int MAX_RETRY_COUNT = 3;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    // ✅ 私有常量：同样规则
    private static final String CACHE_PREFIX = "user:";
    private static final int BATCH_SIZE = 100;
    
    // ❌ 避免：驼峰命名
    private static final String cachePrefix = "user:";  // 错误
}
```

#### 类命名

```java
// ✅ Service 层
public class UserService { }
public class OrderManagementService { }

// ✅ Repository 层
public interface UserRepository extends JpaRepository<User, Long> { }

// ✅ Controller 层
@RestController
public class UserController { }

// ✅ DTO/VO
public class UserDTO { }
public class OrderVO { }

// ✅ Exception
public class BusinessException extends RuntimeException { }
public class UserNotFoundException extends BusinessException { }

// ✅ Config
@Configuration
public class RedisConfig { }

// ✅ Properties
@ConfigurationProperties(prefix = "app.user")
public class UserProperties { }
```

#### 方法命名

```java
public class UserService {
    
    // ✅ 查询方法：get/find/query
    public User getUserById(Long id) { }
    public List<User> findUsersByDept(Long deptId) { }
    public Page<User> queryUsers(UserQuery query) { }
    
    // ✅ 保存方法：save/insert/create
    public User saveUser(User user) { }
    public Long insertUser(UserDTO dto) { }
    public User createUser(UserCreateRequest request) { }
    
    // ✅ 更新方法：update/modify
    public int updateUser(User user) { }
    public void modifyUserStatus(Long userId, Integer status) { }
    
    // ✅ 删除方法：delete/remove
    public int deleteUserById(Long id) { }
    public void removeUsers(List<Long> ids) { }
    
    // ✅ 判断方法：is/has/can
    public boolean isActiveUser(Long userId) { }
    public boolean hasPermission(Long userId, String permission) { }
    public boolean canDelete(Long userId) { }
    
    // ✅ 校验方法：validate/check
    public void validateUser(UserDTO dto) { }
    public boolean checkUsername(String username) { }
    
    // ❌ 避免：过度缩写
    public User getUsrById(Long id) { }  // 错误：usr
    public void updUsr(User u) { }  // 错误：upd, u
}
```

#### 变量命名

```java
public void processOrder(Order order) {
    // ✅ 有意义的变量名
    List<OrderItem> orderItems = order.getItems();
    BigDecimal totalAmount = calculateTotal(orderItems);
    User currentUser = getCurrentUser();
    
    // ✅ 循环变量（简短但清晰）
    for (OrderItem item : orderItems) {
        processItem(item);
    }
    
    for (int i = 0; i < items.size(); i++) {
        // 索引场景使用 i, j, k
    }
    
    // ❌ 避免：单字母变量（除了循环）
    Order o = getOrder();  // 错误
    User u = getUser();  // 错误
    
    // ❌ 避免：过长的变量名
    List<OrderItem> orderItemListFromDatabase = ...;  // 冗余
    // 改为
    List<OrderItem> orderItems = ...;  // 简洁
}
```

---

## 🔧 实施方案

### 阶段 1: 统一依赖注入（1-2 天）

**目标**: 所有 Service/Component 使用 @RequiredArgsConstructor

```bash
# 1. 全局搜索 @Autowired
grep -r "@Autowired" --include="*.java" easywing-platform-*

# 2. 逐个重构
# 添加 @RequiredArgsConstructor
# 移除 @Autowired
# 字段添加 final

# 3. 验证编译
mvn clean compile
```

**修改示例**:
```java
// Before
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
}

// After
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}
```

---

### 阶段 2: 统一日志格式（2-3 天）

**目标**: 所有日志使用占位符，规范日志级别

```bash
# 1. 搜索字符串拼接日志
grep -r "log\\..*+.*+" --include="*.java" easywing-platform-*

# 2. 搜索 String.format 日志
grep -r "log.*String\\.format" --include="*.java" easywing-platform-*

# 3. 批量替换
```

**自动化脚本**:
```bash
#!/bin/bash
# fix-logging.sh

find . -name "*.java" -type f -exec sed -i \
  's/log\.info("\([^"]*\)" + \([^)]*\))/log.info("\1{}", \2)/g' {} +
```

---

### 阶段 3: 提取魔法数字（1-2 天）

**目标**: 将硬编码数字提取到常量类

**创建常量类**:
```java
// RateLimitConstants.java
package com.easywing.platform.gateway.constants;

import java.time.Duration;

public final class RateLimitConstants {
    private RateLimitConstants() {}
    
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
    public static final int DEFAULT_CACHE_MAX_SIZE = 100;
    public static final int DEFAULT_RATE = 100;
    public static final int DEFAULT_CAPACITY = 1000;
}

// JwtConstants.java
package com.easywing.platform.gateway.constants;

public final class JwtConstants {
    private JwtConstants() {}
    
    public static final Duration DEFAULT_JWT_CACHE_TTL = Duration.ofMinutes(5);
    public static final int DEFAULT_JWT_CACHE_SIZE = 10000;
    public static final int DEFAULT_JWK_CACHE_SIZE = 100;
}
```

**修改使用处**:
```java
// Before
this.localBuckets = Caffeine.newBuilder()
    .maximumSize(1000)  // 魔法数字
    .expireAfterAccess(Duration.ofMinutes(10))
    .build();

// After
this.localBuckets = Caffeine.newBuilder()
    .maximumSize(RateLimitConstants.DEFAULT_CACHE_MAX_SIZE)
    .expireAfterAccess(RateLimitConstants.DEFAULT_CACHE_TTL)
    .build();
```

---

### 阶段 4: 代码格式化（1 天）

**使用 Spotless 统一格式**:

```bash
# 检查格式
mvn spotless:check

# 自动格式化
mvn spotless:apply

# 提交前检查
git add .
mvn spotless:check
```

---

## 📊 预期成果

### 代码质量提升

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 代码一致性 | 60% | **95%** | +35% |
| 可维护性 | 7.0 | **8.5** | +1.5 |
| 可读性 | 7.5 | **9.0** | +1.5 |
| 新人上手时间 | 3天 | **1天** | -67% |

---

## ✅ 检查清单

### 依赖注入
- [ ] 所有 Service 使用 @RequiredArgsConstructor
- [ ] 移除所有 @Autowired 字段注入
- [ ] 所有依赖字段标记为 final

### 日志格式
- [ ] 所有日志使用占位符 `{}`
- [ ] 移除字符串拼接
- [ ] 日志级别使用规范
- [ ] 敏感信息脱敏

### 命名约定
- [ ] 常量使用 UPPER_SNAKE_CASE
- [ ] 类名使用 PascalCase
- [ ] 方法名使用 camelCase
- [ ] 变量名有意义且简洁

### 魔法数字
- [ ] 提取所有魔法数字到常量
- [ ] 创建专门的 Constants 类
- [ ] 配置项使用 @ConfigurationProperties

---

## 🛠️ 工具支持

### IDEA 配置

1. **代码风格导入**
```xml
<!-- easywing-codestyle.xml -->
<code_scheme name="EasyWing">
  <option name="USE_SAME_INDENTS" value="true" />
  <option name="RIGHT_MARGIN" value="120" />
  <!-- 更多配置... -->
</code_scheme>
```

2. **Save Actions 插件**
- 保存时自动格式化
- 自动优化 imports
- 自动移除未使用的变量

3. **SonarLint 插件**
- 实时代码质量检查
- 标准规范提示

---

## 📞 实施建议

1. **先统一新代码**: 新功能严格遵守规范
2. **逐步重构旧代码**: 按模块逐步重构
3. **代码评审加强**: PR 时严格检查规范
4. **文档更新**: 更新开发规范文档

---

*预计总工作量: 5-7 天*  
*风险级别: 低*  
*建议优先级: P2（低优先级，持续改进）*
