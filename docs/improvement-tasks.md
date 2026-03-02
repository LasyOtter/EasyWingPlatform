# 🔧 代码改进任务汇总

> 基于代码审查创建的任务列表

## 📊 概述

本文档汇总了代码审查中发现的所有需要修复和改进的问题。按照优先级排序，建议按顺序处理。

---

## 🚨 高优先级 (P0)

### Issue #1: JwtValidationFilter JWK刷新线程资源管理

**类型**: 性能/资源管理  
**文件**: `easywing-platform-gateway/src/main/java/com/easywing/platform/gateway/filter/jwt/JwtValidationFilter.java`  
**行号**: 270-286

**问题描述**:
当前使用普通 `Thread` 进行 JWK Set 刷新，没有优雅关闭机制，可能导致：
- 应用程序关闭时刷新线程仍在运行
- 资源泄漏
- 潜在的空指针异常

**当前代码**:
```java
private void scheduleJwkRefresh() {
    Thread refreshThread = new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(properties.getJwkRefreshInterval().toMillis());
                refreshJwkSet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("JWK Set refresh failed: {}", e.getMessage());
            }
        }
    }, "jwk-refresh");
    refreshThread.setDaemon(true);
    refreshThread.start();
}
```

**建议修复**:
- 使用 `ScheduledExecutorService` 替代普通 Thread
- 添加 `@PreDestroy` 注解实现优雅关闭
- 确保关闭时等待正在执行的刷新完成

**优先级**: P0  
**估计时间**: 2小时

---

### Issue #2: RateLimitFilter LocalTokenBucket 同步阻塞

**类型**: 性能  
**文件**: `easywing-platform-gateway/src/main/java/com/easywing/platform/gateway/filter/ratelimit/RateLimitFilter.java`  
**行号**: 201-247

**问题描述**:
在已经是 `AtomicLong` 的情况下使用 `synchronized` 方法，导致不必要的线程阻塞，降低了限流器的吞吐量。

**当前代码**:
```java
private static class LocalTokenBucket {
    private final AtomicLong tokens;
    // ...
    
    synchronized boolean tryConsume() {
        refill();
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }
}
```

**建议修复**:
使用 CAS (Compare-And-Swap) 实现完全无锁设计：

```java
boolean tryConsume() {
    refill();
    long current;
    do {
        current = tokens.get();
        if (current <= 0) return false;
    } while (!tokens.compareAndSet(current, current - 1));
    return true;
}
```

**优先级**: P0  
**估计时间**: 1小时

---

### Issue #3: 移除 SysUserServiceImpl 中的 TODO

**类型**: 代码清理  
**文件**: `easywing-platform-system/src/main/java/com/easywing/platform/system/service/impl/SysUserServiceImpl.java`  
**行号**: 129

**问题描述**:
存在未完成的 TODO 注释，导出功能未实现。

**当前代码**:
```java
List<SysUserVO> voList = userMapperStruct.toVOList(userPage.getRecords());
// TODO: 写入Excel或进行其他处理
```

**建议修复**:
- 实现具体的 Excel 导出功能，或
- 移除 TODO 并添加适当的日志说明功能待实现

**优先级**: P0  
**估计时间**: 4小时 (如实现导出功能) / 10分钟 (如仅移除TODO)

---

## ⚠️ 中优先级 (P1)

### Issue #4: TokenService 异常信息泄露

**类型**: 安全  
**文件**: `easywing-platform-auth/src/main/java/com/easywing/platform/auth/service/TokenService.java`  
**行号**: 107-109

**问题描述**:
将内部异常详细信息传递给客户端，可能泄露系统内部信息。

**当前代码**:
```java
} catch (Exception e) {
    throw new IllegalArgumentException("Invalid refresh token: " + e.getMessage(), e);
}
```

**建议修复**:
```java
} catch (Exception e) {
    log.error("Refresh token validation failed", e);
    throw new IllegalArgumentException("Invalid or expired refresh token");
}
```

**优先级**: P1  
**估计时间**: 30分钟

---

### Issue #5: GlobalExceptionHandler 异常堆栈泄露

**类型**: 安全  
**文件**: `easywing-platform-framework/easywing-web/src/main/java/com/easywing/platform/web/exception/GlobalExceptionHandler.java`  
**行号**: 514-516

**问题描述**:
未处理的异常可能将堆栈跟踪信息泄露到日志中。

**当前代码**:
```java
} catch (Exception ex) {
    log.error("Unexpected system error [traceId={}] from IP {}: ", traceId, getClientIp(request), ex);
    // ...
}
```

**建议修复**:
确保生产环境日志级别适当，考虑脱敏处理：

```java
} catch (Exception ex) {
    log.error("Unexpected system error [traceId={}] from IP {}: {}",
        traceId, getClientIp(request), ex.getMessage());
    // 不打印完整堆栈到日志，或使用专门的日志脱敏工具
}
```

**优先级**: P1  
**估计时间**: 1小时

---

### Issue #6: 缺少资源清理注解

**类型**: 资源管理  
**涉及文件**: 
- `JwtValidationFilter.java`
- `RateLimitFilter.java`

**问题描述**:
Filter 中使用了缓存和其他资源，但缺少 `@PreDestroy` 清理方法。

**建议修复**:
在相关 Filter 中添加资源清理方法：

```java
@PreDestroy
public void destroy() {
    if (jwkRefreshScheduler != null) {
        jwkRefreshScheduler.shutdown();
    }
    // 清理其他资源
}
```

**优先级**: P1  
**估计时间**: 2小时

---

### Issue #7: 单元测试覆盖不足

**类型**: 测试  
**涉及文件**: 多个核心服务类

**问题描述**:
虽然项目中有测试目录，但建议确保以下核心业务逻辑有充分的单元测试：
- TokenService (JWT 签发、刷新、撤销)
- Security 配置
- 网关过滤器

**建议修复**:
添加或增强以下测试类：
- `TokenServiceTest.java` - 补充更多场景测试
- `JwtValidationFilterTest.java` - 新增
- `RateLimitFilterTest.java` - 新增

**优先级**: P1  
**估计时间**: 8小时

---

## 📝 低优先级 (P2)

### Issue #8: 代码风格一致性

**类型**: 代码质量  
**涉及文件**: 多个

**问题描述**:
- 部分类使用 `@RequiredArgsConstructor`
- 部分类使用构造函数注入
- 日志格式不一致

**建议修复**:
- 统一使用 `@RequiredArgsConstructor` 风格
- 统一日志格式

**优先级**: P2  
**估计时间**: 4小时

---

### Issue #9: 魔法数字提取

**类型**: 代码质量  
**涉及文件**:
- `RateLimitFilter.java`
- `JwtValidationFilter.java`

**问题描述**:
存在硬编码的数字，应提取到常量或配置类中。

**当前代码**:
```java
.expireAfterWrite(Duration.ofHours(1))
.maximumSize(100)
```

**建议修复**:
```java
// RateLimitConstants.java
public class RateLimitConstants {
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
    public static final int DEFAULT_CACHE_MAX_SIZE = 100;
}
```

**优先级**: P2  
**估计时间**: 2小时

---

### Issue #10: API Versioning 配置检查

**类型**: 功能完整性  
**涉及文件**: `easywing-platform-framework/easywing-web/`

**问题描述**:
项目提到 API Versioning，但需确认所有 Controller 都正确使用。

**建议修复**:
检查并确保所有 Controller 正确使用 `@ApiVersion` 注解。

**优先级**: P2  
**估计时间**: 2小时

---

## 📋 实施计划

### 阶段1: 紧急修复 (1-2周)
- [ ] Issue #1: JWK刷新线程资源管理
- [ ] Issue #2: 限流器同步优化
- [ ] Issue #4: 异常信息泄露修复

### 阶段2: 稳定性增强 (2-3周)
- [ ] Issue #3: 移除TODO并实现导出功能
- [ ] Issue #5: 日志安全性修复
- [ ] Issue #6: 资源清理

### 阶段3: 质量提升 (3-4周)
- [ ] Issue #7: 单元测试覆盖
- [ ] Issue #8: 代码风格一致性
- [ ] Issue #9: 魔法数字提取
- [ ] Issue #10: API Versioning 检查

---

## 📊 时间估算

| 阶段 | 任务数 | 估计时间 |
|------|--------|----------|
| 阶段1 | 4 | 6小时 |
| 阶段2 | 3 | 12小时 |
| 阶段3 | 4 | 16小时 |
| **总计** | **11** | **~34小时** |

---

*本文档由代码审查自动生成，最后更新: 2026*
