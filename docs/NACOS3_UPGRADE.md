# Nacos Server 3.x 升级说明

## 版本

- 服务端镜像：`nacos/nacos-server:v3.3.0`（原 `v2.3.x`）
- 客户端：`nacos-client 3.0.3`（由 `spring-cloud-alibaba 2025.0.0.0` 传递管理）

## 3.x 关键变化

- 控制台拆分为独立端口 `8080`，主服务仍为 `8848`，gRPC 为 `9848/9849`。
- 默认命名空间 ID 由空字符串改为 `public`，旧数据通过兼容模式双向同步。
- 客户端 API 鉴权默认开启，必须显式配置 `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`，否则启动失败。
- 数据库 schema 与 2.x 存在差异，升级前必须备份。

## docker-compose 变更

`docker-compose.yml` 与 `easywing-platform-samples/docker-compose.yml` 已更新：

- 镜像升级到 `v3.3.0`。
- 增加 `8081:8080` 控制台端口（宿主机 `8080` 保留给 API 网关）。
- 增加认证环境变量，`NACOS_AUTH_TOKEN` 为 Base64 编码、解码后 32 字节的密钥。

## 数据库升级步骤

1. 备份现有 `nacos` 数据库。
2. 从 Nacos 3.3.0 发行包获取 `distribution/conf/mysql-schema.sql`。
3. 创建库并导入 schema：

   ```sql
   CREATE DATABASE IF NOT EXISTS nacos DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
   USE nacos;
   SOURCE /path/to/mysql-schema.sql;
   ```

4. 若已有 2.x 数据，先对比 2.x 与 3.x 的 `mysql-schema.sql` 差异，再对存量库执行增量 `ALTER`，切勿直接覆盖。

## 验证

- 控制台：`http://localhost:8081`（首次访问需初始化管理员密码）。
- 客户端注册：`8848`。
- 确认 `spring-cloud-alibaba` 的 `nacos-client 3.0.3` 能正常注册与拉取配置。

## 回滚

- 镜像回退到 `v2.3.x`，移除新增的认证环境变量与 `8080` 端口，恢复备份数据库。
