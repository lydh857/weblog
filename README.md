# Weblog

<p align="left">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.4.13-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot"></a>
  <a href="https://nuxt.com/"><img src="https://img.shields.io/badge/Nuxt-4.x-green?style=flat-square&logo=nuxt" alt="Nuxt"></a>
  <a href="https://vuejs.org/"><img src="https://img.shields.io/badge/Vue-3.x-4fc08d?style=flat-square&logo=vue.js" alt="Vue"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java"></a>
  <a href="https://www.mysql.com/"><img src="https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql" alt="MySQL"></a>
  <a href="https://redis.io/"><img src="https://img.shields.io/badge/Redis-7.2-red?style=flat-square&logo=redis" alt="Redis"></a>
  <a href="/LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" alt="License"></a>
</p>

基于 Spring Boot 3 + Nuxt 4 的全栈博客系统，支持 SSR、全文搜索、互动系统、广告管理等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.4, MyBatis-Plus, Sa-Token, Lucene |
| 用户端 | Nuxt 4 (SSR), SCSS, VueUse |
| 管理端 | Nuxt 4 (SPA), Element Plus |
| 数据库 | MySQL 8.0, Redis 7.2 |
| 部署 | Docker Compose, Nginx |

## 核心功能

- 📝 文章管理（CRUD、定时发布、置顶、自动保存）
- 🔍 Lucene 全文搜索（中文分词、高亮、NRT）
- 💬 互动系统（点赞、收藏、评论、敏感词过滤）
- 📢 广告/公告系统（申请→审核→展示）
- 🔒 访问控制（每日阅读限制、设备指纹、滑块验证码）
- 📊 文章排行榜（日/周/月/总榜）
- 🐙 GitHub OAuth 登录
- 🤖 AI 辅助写作
- 🛡️ 安全防护（XSS 过滤、限流、审计日志、BCrypt 加密）

## 项目结构

```
weblog-backend/              # Spring Boot 后端（Maven 多模块）
├── weblog-common/           # 公共工具、统一响应 Result<T>
├── weblog-infra-redis/      # Redis 缓存/限流
├── weblog-infra-oss/        # 对象存储（OSS / R2）
├── weblog-infra-lucene/     # 全文搜索索引
├── weblog-infra-security/   # XSS 防护、风控
├── weblog-infra-captcha/    # 滑块验证码
├── weblog-infra-ai/         # AI 服务集成
├── weblog-module-system/    # 系统管理（用户/角色/配置/日志）
├── weblog-module-content/   # 内容管理（文章/分类/标签/广告）
├── weblog-module-interaction/ # 互动（评论/点赞/关注/公告）
└── weblog-api/              # 聚合启动模块（Controller + Flyway）
weblog-user/                 # 用户端 Nuxt SSR
weblog-admin/                # 管理端 Nuxt SPA
nginx/                       # Nginx 配置
deploy/                      # 部署脚本
database/                    # 数据库初始化 SQL
```

## 快速开始

### 1. 启动基础服务

```bash
docker compose up -d
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际配置
```

### 3. 启动后端

```bash
cd weblog-backend
mvn clean install -DskipTests
mvn spring-boot:run -pl weblog-api -DskipTests
```

### 4. 启动前端

```bash
# 用户端
cd weblog-user
pnpm install
pnpm dev

# 管理端
cd weblog-admin
pnpm install
pnpm dev
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 用户端 | http://localhost:3000 |
| 管理端 | http://localhost:3001 |
| API 文档 | http://localhost:9091/doc.html |

### 默认账号

数据库初始化后会自动创建以下种子账号，**所有账号初始密码均为 `Weblog@123`**：

| 角色 | 邮箱 | 说明 |
|------|------|------|
| 管理员 | `admin@blog.test` | 可登录管理端 |
| 普通用户 | `zhangsan@test.com` | 演示用户 |
| 普通用户 | `lisi@test.com` | 演示用户 |

> ⚠️ **生产环境部署后请立即登录管理端修改默认密码！**

## 环境要求

- JDK 21+
- Node.js 20+ / pnpm 8+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 7.0+

## 生产部署

### 1. 配置环境变量

```bash
cp .env.example .env.prod
# 编辑 .env.prod，使用强随机密钥（可用 deploy/generate-secrets.sh 生成）
```

### 2. 配置 Nginx

编辑 `nginx/nginx.conf`，将 `server_name` 和 SSL 证书路径替换为你的域名和证书。

### 3. 部署

```bash
chmod +x deploy/deploy.sh
./deploy/deploy.sh
```

详细部署指南请参考 [docs/deployment-guide.md](docs/deployment-guide.md)。

## 开发指南

请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解开发流程、代码风格和提交规范。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，欢迎自由使用和贡献。
