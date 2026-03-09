# Weblog Backend

博客系统后端 - Spring Boot 3.2.x + JDK 21 多模块架构

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.8+
- Docker & Docker Compose（用于 MySQL 和 Redis）

### 2. 启动数据库

在项目根目录（`c:\weblog`）执行：

```bash
docker-compose up -d
```

这将启动：
- MySQL 8.0.36（端口 3307）
- Redis 7.2（端口 6380）

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并修改配置：

```bash
cp .env.example .env
```

**重要配置项：**
- `DB_USERNAME` / `DB_PASSWORD`：数据库账号密码
- `REDIS_PASSWORD`：Redis 密码
- `JWT_SECRET`：JWT 密钥（生产环境必须修改为强随机字符串）

### 4. 编译项目

```bash
mvn clean compile
```

### 5. 运行应用

```bash
mvn spring-boot:run -pl weblog-api
```

或者在 IDE 中运行 `BlogApiApplication.java`

应用将在 `http://localhost:8080` 启动

### 6. API 文档

启动后访问：
- Knife4j 文档：http://localhost:8080/doc.html

## 项目结构

```
weblog-backend/
├── weblog-common/              # 公共模块（Result、异常、工具类）
├── weblog-infra-redis/         # Redis 基础设施
├── weblog-infra-oss/           # 阿里云 OSS 基础设施
├── weblog-infra-lucene/        # Lucene 全文检索
├── weblog-infra-security/      # 安全框架（JWT/XSS/CSRF/限流）
├── weblog-module-system/       # 系统模块（用户认证）
├── weblog-module-content/      # 内容模块（文章管理）
├── weblog-module-interaction/  # 互动模块（点赞/评论）
└── weblog-api/                 # 聚合应用（API 入口）
```

## 安全说明

### 密钥管理

- ❌ **禁止**在代码中硬编码密码、密钥
- ✅ **必须**使用环境变量（`.env` 文件）
- ✅ `.env` 文件已加入 `.gitignore`，不会提交到 Git

### 生产环境部署

1. 修改 `.env` 中的所有密码和密钥
2. `JWT_SECRET` 必须使用至少 256 位的强随机字符串
3. 数据库密码必须使用强密码
4. 确保 `.env` 文件权限为 `600`（仅所有者可读写）

## 开发规范

- JDK 21 虚拟线程、ZGC 垃圾回收
- Spring Boot 3.2.x
- MyBatis-Plus（自动防 SQL 注入）
- Sa-Token（JWT 认证）
- 所有用户输入必须 XSS 过滤
- 所有 API 必须权限验证

## 生产环境部署

### 1. 准备生产环境配置

```bash
# 复制生产环境配置模板
cp .env.prod.example .env.prod

# 编辑配置文件，修改所有密码和密钥
vim .env.prod

# 设置文件权限（仅所有者可读写）
chmod 600 .env.prod
```

### 2. 生成强随机密钥

```bash
# 生成 JWT 密钥（256位）
openssl rand -base64 64

# 生成数据库密码
openssl rand -base64 32
```

### 3. 打包应用

```bash
mvn clean package -DskipTests
```

生成的 JAR 包位于：`weblog-api/target/weblog-api-1.0.0-SNAPSHOT.jar`

### 4. 启动应用

```bash
# 方式1：使用环境变量文件
export $(cat .env.prod | xargs) && java -jar weblog-api/target/weblog-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod

# 方式2：直接指定环境变量
java -jar weblog-api/target/weblog-api-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=localhost \
  --DB_PASSWORD=your_password \
  --JWT_SECRET=your_jwt_secret
```

### 5. 使用 systemd 管理服务

创建 `/etc/systemd/system/weblog-api.service`：

```ini
[Unit]
Description=Weblog API Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=weblog
WorkingDirectory=/opt/weblog
EnvironmentFile=/opt/weblog/.env.prod
ExecStart=/usr/bin/java -jar /opt/weblog/weblog-api.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable weblog-api
sudo systemctl start weblog-api
sudo systemctl status weblog-api
```

### 6. 日志查看

```bash
# 查看应用日志
tail -f /var/log/weblog/weblog-api-info.log
tail -f /var/log/weblog/weblog-api-error.log

# 查看 systemd 日志
journalctl -u weblog-api -f
```

### 7. 健康检查

```bash
# 检查应用健康状态
curl http://localhost:8080/actuator/health
```

## 环境对比

| 配置项 | 开发环境 (dev) | 生产环境 (prod) |
|--------|---------------|----------------|
| 数据库端口 | 3307 | 3306 |
| Redis端口 | 6380 | 6379 |
| 日志级别 | DEBUG | INFO |
| 日志输出 | 控制台 | 文件（按天切割） |
| 连接池大小 | 默认 | 20 |
| SSL | 关闭 | 开启 |
| 错误堆栈 | 显示 | 隐藏 |

## 相关文档

- [需求文档](../.kiro/specs/blog-system/requirements.md)
- [设计文档](../.kiro/specs/blog-system/design.md)
- [任务清单](../.kiro/specs/blog-system/tasks.md)

## 运维配置：OSS 与 CDN 安全

### OSS Bucket 私有配置（禁止公网直访）

生产环境必须将 OSS Bucket 设为私有读写，所有访问通过签名 URL 或 CDN 回源鉴权。

1. 登录阿里云 OSS 控制台
2. 选择对应 Bucket → 权限管理 → 读写权限
3. 设置为 **私有（private）**
4. 配置 CDN 回源鉴权：
   - CDN 控制台 → 域名管理 → 回源配置
   - 开启 **私有 Bucket 回源**
   - 授权 CDN 访问 OSS Bucket

验证方式：直接访问 OSS 域名（非 CDN）应返回 403 AccessDenied。

### CDN Referer 防盗链配置

防止其他网站盗用 CDN 资源，节省流量费用。

1. 登录阿里云 CDN 控制台
2. 选择加速域名 → 访问控制 → Referer 防盗链
3. 配置：
   - 类型：**白名单**
   - 规则：`yourdomain.com`（替换为实际域名）
   - 允许空 Referer：**否**（生产环境建议关闭）

验证方式：使用 curl 带不同 Referer 头访问 CDN 资源，非白名单域名应返回 403。

```bash
# 正常访问（应返回 200）
curl -H "Referer: https://yourdomain.com" https://cdn.yourdomain.com/images/test.jpg -o /dev/null -w "%{http_code}"

# 盗链访问（应返回 403）
curl -H "Referer: https://evil.com" https://cdn.yourdomain.com/images/test.jpg -o /dev/null -w "%{http_code}"
```
