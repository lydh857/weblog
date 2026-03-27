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

## P0 API 回归（本地/CI）

### GitHub Actions

- 工作流文件：`.github/workflows/p0-api-regression.yml`
- 触发方式：Actions 页面手动触发 `P0 API Regression`
- 关键输入参数：
  - `authToken`：可选，`Satoken` 的纯值（不带 `Satoken=` 前缀）
  - `checkMalformedCommentLike`：可选，是否开启评论点赞缓存脏值容错断言
  - `malformedCheckPostId` / `malformedCheckCommentId` / `malformedExpectedLikeCount`：可选，容错断言目标
- 可选 Secret：`P0_REGRESSION_AUTH_TOKEN`
  - 若 `authToken` 输入为空，工作流会回退读取该 Secret
  - 若两者都为空，工作流只跑无登录态断言（登录态断言自动跳过）

### 本地生成 Satoken（用于 CI 登录态回归）

登录接口要求 `X-Captcha-Token`，可用下面脚本在本地生成一次性 `verifyToken` 并换取 `Satoken`：

```powershell
param(
  [Parameter(Mandatory = $true)] [string]$Email,
  [Parameter(Mandatory = $true)] [string]$Password,
  [string]$BaseUrl = "http://127.0.0.1:9091",
  [string]$ClientIp = "127.0.0.1"
)

$envMap = @{}
Get-Content "./.env" | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $parts = $_.Split('=', 2)
  $envMap[$parts[0].Trim()] = $parts[1].Trim()
}

$captchaSecret = $envMap["CAPTCHA_SECRET_KEY"]
$redisPassword = $envMap["REDIS_PASSWORD"]
if ([string]::IsNullOrWhiteSpace($captchaSecret)) { throw "CAPTCHA_SECRET_KEY 未配置" }
if ([string]::IsNullOrWhiteSpace($redisPassword)) { throw "REDIS_PASSWORD 未配置" }

$tokenId = [Guid]::NewGuid().ToString()
$createTime = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$signPayload = "$tokenId|$ClientIp|$createTime"

$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($captchaSecret))
$sigBytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($signPayload))
$signature = [Convert]::ToBase64String($sigBytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
$verifyToken = "$tokenId.$signature"

$verifyJson = "{`"clientIp`":`"$ClientIp`",`"createTime`":$createTime}"
docker exec blog-redis redis-cli -a $redisPassword -p 6379 SETEX ("captcha:verify:" + $tokenId) 300 $verifyJson | Out-Null

$loginBody = @{
  email = $Email
  password = $Password
  rememberMe = $false
} | ConvertTo-Json

$resp = Invoke-WebRequest -Method POST -Uri "$BaseUrl/api/admin/auth/login" -Headers @{
  "X-Captcha-Token" = $verifyToken
  "Origin" = "http://localhost:3001"
  "Referer" = "http://localhost:3001/"
} -Body $loginBody -ContentType "application/json"

$payload = $resp.Content | ConvertFrom-Json
if ([int]$payload.code -ne 200) {
  throw "登录失败，code=$($payload.code), message=$($payload.message)"
}

$setCookies = @($resp.Headers["Set-Cookie"])
$satoken = $null
foreach ($cookie in $setCookies) {
  if ($cookie -match 'Satoken=([^;]+)') {
    $satoken = $matches[1]
    break
  }
}
if ([string]::IsNullOrWhiteSpace($satoken)) {
  throw "未在 Set-Cookie 中提取到 Satoken"
}

Write-Output $satoken
```

将输出的 token 作为工作流 `authToken` 输入，或保存为仓库 Secret `P0_REGRESSION_AUTH_TOKEN`。

### 分支保护生效说明（私有仓库）

- 当前仓库是私有仓库，`Branch protection rules` 在当前套餐下可能显示 `Not enforced`。
- 即使规则暂未被平台强制执行，也建议按同样标准执行提交流程，避免回归。

### 推荐合并流程（团队约定）

1. 所有改动先提交到功能分支。
2. 通过 Pull Request 合并到 `master`，不直接向 `master` 推送功能改动。
3. Pull Request 必须通过 `P0 API Regression` 后再合并。
4. 合并后同步推送 GitHub 主仓库与 Gitee 镜像仓库。


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
