-- EasyWing Platform - MySQL 初始化脚本
-- 由 docker-compose 挂载到 /docker-entrypoint-initdb.d，仅在数据卷首次创建时执行。
--
-- 注意：各服务的表结构由 Flyway 自动迁移（src/main/resources/db/migration），
-- 这里只负责创建库本身并授权，避免与 Flyway 的 schema 管理冲突。

CREATE DATABASE IF NOT EXISTS easywing_platform
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easywing_system
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easywing_auth
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easywing_ai
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easywing_led
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 开发用账号（与 compose 中的 MYSQL_USER / MYSQL_PASSWORD 一致）
CREATE USER IF NOT EXISTS 'easywing'@'%' IDENTIFIED BY 'easywing123456';
GRANT ALL PRIVILEGES ON easywing_platform.* TO 'easywing'@'%';
GRANT ALL PRIVILEGES ON easywing_system.* TO 'easywing'@'%';
GRANT ALL PRIVILEGES ON easywing_auth.* TO 'easywing'@'%';
GRANT ALL PRIVILEGES ON easywing_ai.* TO 'easywing'@'%';
GRANT ALL PRIVILEGES ON easywing_led.* TO 'easywing'@'%';
FLUSH PRIVILEGES;
