# EasyWing Data Starter

增强型数据访问组件，基于 MyBatis-Plus，提供多租户、数据权限、审计功能。

## 特性

- 多租户支持
- 数据权限控制
- 自动审计（创建人、创建时间等）
- 动态数据源
- 分页工具

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-data</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
easywing:
  data:
    enabled: true
    tenant:
      enabled: true
      column: tenant_id
    datascope:
      enabled: true
      type: all
    audit:
      enabled: true
```

## 基础实体

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity<Long> {
    
    private String name;
    private String email;
}
```

## 多租户

自动在 SQL 中注入租户 ID：

```java
@Service
public class UserService {
    
    public List<User> list() {
        // 自动添加 WHERE tenant_id = ?
        return userMapper.selectList(null);
    }
}
```

### 设置租户

```java
TenantContext.setTenantId(tenantId);
// 或通过 ThreadLocal
```

### 忽略租户

```java
@IgnoreDataScope
public List<User> listAll() {
    return userMapper.selectList(null);
}
```

## 数据权限

### 注解方式

```java
@DataScope(deptAlias = "d", permission = "user:list")
public class User {
    // 
}
```

### 编程方式

```java
public List<User> listWithDataScope() {
    DataScopeContext.setDataScope("dept", Arrays.asList(1L, 2L));
    return userMapper.selectList(null);
}
```

## 审计功能

自动填充：

```java
public class User extends BaseEntity<Long> {
    
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
```

## 分页工具

```java
public IPage<User> page(int pageNum, int pageSize) {
    return new PageUtils().getPage(pageNum, pageSize);
}

// 或
public IPage<User> page(PageParam param) {
    IPage<User> page = new Page<>(param.getPageNum(), param.getPageSize());
    return userMapper.selectPage(page, new QueryWrapper<>());
}
```

## 动态数据源

```java
// 切换数据源
DynamicDataSourceContextHolder.setDataSourceKey("slave1");

try {
    return userMapper.selectList(null);
} finally {
    DynamicDataSourceContextHolder.clear();
}
```
