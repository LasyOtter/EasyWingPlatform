# EasyWing Auth Service

统一认证授权服务，提供JWT令牌签发、刷新、注销及JWK Set公钥端点。

## 功能特性

- **JWT令牌签发**：使用RS256算法签发访问令牌（Access Token）和刷新令牌（Refresh Token）
- **令牌刷新**：使用刷新令牌换取新的令牌对，旧刷新令牌自动加入黑名单
- **令牌注销**：将令牌加入Redis黑名单，立即失效
- **JWK Set端点**：暴露RSA公钥供网关和资源服务器验证JWT签名

## API端点

| 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|-------------|
| POST | `/token/login` | 用户名密码登录 | 否 |
| POST | `/token/refresh` | 刷新令牌 | 否 |
| POST | `/token/logout` | 注销登录 | 是（Bearer Token） |
| GET  | `/.well-known/jwks.json` | JWK Set公钥 | 否 |

## 网关集成

网关的JWT白名单已配置以下路径（无需携带Token即可访问）：
- `/api/auth/token/login`
- `/api/auth/token/refresh`
- `/api/auth/.well-known/jwks.json`

网关会从成功验证的JWT中提取用户上下文，并通过以下请求头转发给下游服务：
- `X-User-Id`：用户ID（JWT `sub` 声明）
- `X-Username`：用户名（JWT `preferred_username` 声明）
- `X-Roles`：角色列表，逗号分隔（JWT `roles` 声明）
- `X-Tenant-Id`：租户ID（JWT `tenant_id` 声明）

## 配置说明

```yaml
easywing:
  auth:
    jwt:
      issuer: https://auth.easywing.com          # JWT签发者
      access-token-ttl: 30m                       # 访问令牌有效期
      refresh-token-ttl: 7d                       # 刷新令牌有效期
      key-id: easywing-key-1                      # RSA密钥ID
      roles-claim-name: roles                     # 角色声明名称
      tenant-id-claim-name: tenant_id             # 租户ID声明名称
```

## 内置用户（开发/测试用）

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ROLE_ADMIN, ROLE_USER |
| user | user123 | ROLE_USER |

> **生产环境**：将 `UserDetailsService` 替换为数据库查询实现。

## 快速开始

```bash
# 启动依赖（Redis）
docker-compose up -d redis

# 启动认证服务
mvn spring-boot:run -pl easywing-platform-auth

# 登录获取令牌
curl -X POST http://localhost:8085/token/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 获取JWK Set公钥
curl http://localhost:8085/.well-known/jwks.json
```
