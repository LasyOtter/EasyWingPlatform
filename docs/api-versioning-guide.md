# 🔄 API 版本管理指南

> 更新时间: 2026-11-14

---

## 📋 概述

EasyWing Platform 采用基于 URL 路径的 API 版本控制策略，通过 `@ApiVersion` 注解实现版本化管理。

---

## 🎯 版本策略

### 版本格式
```
/api/v{version}/{resource}

示例:
- /api/v1/users
- /api/v2/users
- /api/v1.1/orders
```

### 版本规则
- **主版本号（v1, v2）**: 不兼容的 API 变更
- **次版本号（v1.1, v1.2）**: 向后兼容的功能新增
- **修订号**: 不体现在 URL 中，仅用于内部跟踪

---

## 📝 使用说明

### 1. 类级别版本控制

**推荐用法**：整个 Controller 使用同一版本

```java
@RestController
@RequestMapping("/api/users")
@ApiVersion("v1")
public class UserControllerV1 {
    
    @GetMapping
    public Page<UserVO> getUsers() {
        // v1 版本实现
        // 实际路径: GET /api/v1/users
    }
    
    @GetMapping("/{id}")
    public UserVO getUserById(@PathVariable Long id) {
        // 实际路径: GET /api/v1/users/{id}
    }
}
```

### 2. 方法级别版本控制

**高级用法**：同一 Controller 中不同方法使用不同版本

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    @ApiVersion("v1")
    public Page<UserVO> getUsersV1() {
        // 实际路径: GET /api/v1/users
    }
    
    @GetMapping
    @ApiVersion("v2")
    public PagedResponse<UserDetailVO> getUsersV2() {
        // 实际路径: GET /api/v2/users
        // v2 返回更详细的信息
    }
}
```

### 3. 版本弃用标记

```java
@RestController
@RequestMapping("/api/users")
@ApiVersion(
    value = "v1",
    deprecated = true,
    deprecationMessage = "此版本将在 2027-01-01 下线",
    replacedBy = "v2"
)
public class UserControllerV1 {
    
    @GetMapping
    public Page<UserVO> getUsers() {
        // 响应头会包含弃用警告
        // Deprecation: true
        // Sunset: 2027-01-01
        // Link: </api/v2/users>; rel="successor-version"
    }
}
```

### 4. 默认版本

```java
@RestController
@RequestMapping("/api/users")
@ApiVersion(value = "v2", isDefault = true)
public class UserControllerV2 {
    
    @GetMapping
    public PagedResponse<UserDetailVO> getUsers() {
        // 可通过 /api/users 或 /api/v2/users 访问
    }
}
```

---

## 🏗️ 项目结构建议

### 独立版本 Controller（推荐）

```
com.easywing.platform.system.controller
├── v1
│   ├── UserControllerV1.java
│   ├── RoleControllerV1.java
│   └── DeptControllerV1.java
└── v2
    ├── UserControllerV2.java
    ├── RoleControllerV2.java
    └── DeptControllerV2.java
```

**优点**:
- 版本隔离清晰
- 便于维护和下线
- 避免代码混乱

### 共享版本 Controller（适用于小型项目）

```
com.easywing.platform.system.controller
├── UserController.java  (包含 v1, v2 方法)
├── RoleController.java
└── DeptController.java
```

---

## 📊 当前版本状态检查

### 已使用版本
```
✅ TokenController              /api/v1/token/*
⚠️  UserController (sample)     /api/users/*      (无版本)
⚠️  SysUserController          /api/system/users/* (无版本)
⚠️  SysRoleController          /api/system/roles/* (无版本)
```

### 建议改进

#### 1. TokenController - 已正确使用 ✅
```java
@RestController
@RequestMapping("/api/v1/token")
public class TokenController {
    // 已经包含版本号，符合规范
}
```

#### 2. SysUserController - 需要添加版本 ⚠️

**当前**:
```java
@RestController
@RequestMapping("/api/system/users")
public class SysUserController {
    // 缺少版本控制
}
```

**推荐改为**:
```java
@RestController
@RequestMapping("/api/system/users")
@ApiVersion(value = "v1", isDefault = true)
public class SysUserController {
    // 添加版本注解，同时设为默认版本保持兼容性
}
```

#### 3. 示例 UserController - 需要添加版本 ⚠️

**当前**:
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    // 缺少版本控制
}
```

**推荐改为**:
```java
@RestController
@RequestMapping("/api/users")
@ApiVersion("v1")
public class UserController {
    // 添加版本注解
}
```

---

## 🔄 版本升级指南

### 场景 1: 添加新功能（向后兼容）

```java
// v1 - 原有实现
@RestController
@RequestMapping("/api/users")
@ApiVersion("v1")
public class UserControllerV1 {
    
    @GetMapping
    public Page<UserVO> getUsers(
        @RequestParam(required = false) String keyword
    ) {
        // 基础查询
    }
}

// v1.1 - 新增功能（向后兼容）
@RestController
@RequestMapping("/api/users")
@ApiVersion("v1.1")
public class UserControllerV1_1 {
    
    @GetMapping
    public Page<UserVO> getUsers(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String department  // 新增参数
    ) {
        // 增强查询，但不破坏兼容性
    }
}
```

### 场景 2: 不兼容变更（升级主版本）

```java
// v1 - 旧版本
@RestController
@RequestMapping("/api/users")
@ApiVersion(
    value = "v1",
    deprecated = true,
    deprecationMessage = "请迁移到 v2 版本",
    replacedBy = "v2"
)
public class UserControllerV1 {
    
    @GetMapping
    public Page<UserVO> getUsers() {
        // 返回简单的用户信息
        return userService.listUsers();
    }
}

// v2 - 新版本（不兼容变更）
@RestController
@RequestMapping("/api/users")
@ApiVersion(value = "v2", isDefault = true)
public class UserControllerV2 {
    
    @GetMapping
    public PagedResponse<UserDetailVO> getUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) UserQueryDTO query
    ) {
        // 新的分页方式和响应格式
        return userService.searchUsers(page, size, query);
    }
}
```

---

## 📋 检查清单

### 新增 API 检查
- [ ] 是否添加了 `@ApiVersion` 注解
- [ ] 版本号是否符合规范（v1, v2, v1.1）
- [ ] 是否与现有版本冲突
- [ ] 是否更新了 API 文档

### 修改 API 检查
- [ ] 是否为不兼容变更
- [ ] 如是，是否创建新版本
- [ ] 如否，是否在当前版本修改
- [ ] 是否标记旧版本为 deprecated

### 下线 API 检查
- [ ] 是否提前通知（建议 3-6 个月）
- [ ] 是否标记为 deprecated
- [ ] 是否提供替代版本
- [ ] 是否有迁移文档

---

## 🔧 实施计划

### 阶段 1: 添加版本注解（立即执行）

```bash
# 为现有 Controller 添加 @ApiVersion 注解
# 设置为默认版本以保持向后兼容

1. SysUserController      → @ApiVersion(value = "v1", isDefault = true)
2. SysRoleController      → @ApiVersion(value = "v1", isDefault = true)
3. SysDeptController      → @ApiVersion(value = "v1", isDefault = true)
4. SysMenuController      → @ApiVersion(value = "v1", isDefault = true)
... 其他 Controller
```

### 阶段 2: 重构现有 API（1-2 周）

```bash
# 对于计划升级的 API
1. 保留现有 Controller 作为 v1
2. 创建新的 v2 Controller
3. 标记 v1 为 deprecated
4. 更新文档
```

### 阶段 3: 下线旧版本（3-6 个月后）

```bash
# 在充分通知后
1. 删除 deprecated 的版本
2. 清理相关代码和测试
3. 更新文档
```

---

## 📚 最佳实践

### ✅ 推荐做法

1. **明确版本策略**: 在项目初期确定版本规则
2. **独立 Controller**: 每个版本使用独立的 Controller 类
3. **设置默认版本**: 为最新稳定版设置 isDefault = true
4. **提前弃用警告**: 提前 3-6 个月标记 deprecated
5. **完整文档**: 每个版本都有完整的 API 文档
6. **向后兼容**: 小版本尽量保持向后兼容

### ❌ 避免做法

1. **频繁升级主版本**: 避免给用户造成困扰
2. **立即删除旧版本**: 需要给用户足够的迁移时间
3. **混合版本代码**: 不要在同一个类中混合多个版本的代码
4. **缺少弃用通知**: 突然下线 API 会影响用户体验

---

## 🎯 预期收益

- ✅ **清晰的版本管理**: 每个 API 版本职责明确
- ✅ **平滑的升级路径**: 用户可以逐步迁移
- ✅ **降低维护成本**: 旧版本可以安全下线
- ✅ **提升用户体验**: 不会突然破坏现有集成

---

## 📞 常见问题

**Q: 何时需要升级主版本？**  
A: 当 API 发生不兼容变更时，如：响应格式变更、参数含义变更、行为变更等。

**Q: 可以同时维护多少个版本？**  
A: 建议同时维护 2-3 个版本，最多不超过 3 个主版本。

**Q: 如何处理紧急 Bug 修复？**  
A: Bug 修复应该应用到所有活跃版本，不需要升级版本号。

**Q: 是否需要为每个微服务都做版本管理？**  
A: 是的，每个对外暴露的微服务都应该有清晰的版本管理。

---

*工作量估算: 2-3 小时*  
*优先级: P2（中低优先级）*  
*建议时机: 新功能开发前统一规划*
