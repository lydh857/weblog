# Weblog 博客系统

基于 Spring Boot 3 + Nuxt 4 的全栈博客系统，支持 SSR、全文搜索、互动系统、广告管理等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.2, MyBatis-Plus, Sa-Token, Lucene |
| 用户端 | Nuxt 4 (SSR), SCSS, VueUse |
| 管理端 | Nuxt 4 (SPA), Element Plus |
| 数据库 | MySQL 8.0, Redis 7.2 |
| 部署 | Docker Compose, Nginx, PM2 |

## 项目结构

```
weblog-backend/          # Spring Boot 后端（多模块）
├── weblog-common/       # 公共工具、统一响应
├── weblog-infra-*/      # 基础设施（Redis/OSS/Lucene/Security）
├── weblog-module-*/     # 业务模块（System/Content/Interaction）
└── weblog-api/          # 聚合启动模块
weblog-user/             # 用户端 Nuxt SSR
weblog-admin/            # 管理端 Nuxt SPA
nginx/                   # Nginx 配置
deploy/                  # 部署脚本
```

## 快速开始（开发环境）

### 1. 启动基础服务

```bash
docker compose up -d
```

### 2. 启动后端

```bash
cd weblog-backend
mvn clean install -DskipTests
mvn spring-boot:run -pl weblog-api -DskipTests
```

### 3. 启动用户端

```bash
cd weblog-user
pnpm install
pnpm dev
```

### 4. 启动管理端

```bash
cd weblog-admin
pnpm install
pnpm dev
```

### 访问地址

- 用户端: http://localhost:3000
- 管理端: http://localhost:3001
- API 文档: http://localhost:9091/doc.html


## 生产部署

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际配置
```

### 2. 申请 SSL 证书

```bash
chmod +x deploy/ssl-init.sh
./deploy/ssl-init.sh yourdomain.com admin@yourdomain.com
```

### 3. 一键部署

```bash
chmod +x deploy/deploy.sh
./deploy/deploy.sh
```

### 4. 配置开机自启

```bash
sudo cp deploy/weblog-api.service /etc/systemd/system/
sudo systemctl enable weblog-api
sudo systemctl start weblog-api
```

## 核心功能

- 文章管理（CRUD、定时发布、置顶、自动保存）
- Lucene 全文搜索（中文分词、高亮、NRT）
- 互动系统（点赞、收藏、评论、敏感词过滤）
- 广告/公告系统（申请→审核→展示）
- 访问控制（每日3篇限制、设备指纹、滑块验证解锁）
- 文章排行榜（日/周/月/总榜）
- GitHub OAuth 登录
- 阿里云 OSS 图片管理
- 安全防护（XSS过滤、限流、审计日志、BCrypt加密）

## 环境要求

- JDK 21+
- Node.js 20+
- pnpm 8+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 7.0+
