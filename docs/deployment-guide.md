# Weblog 生产部署完整教程

本文基于当前 Weblog 项目编写，目标是让没有部署经验的人也能按步骤完成上线。

当前项目的生产部署方式是：推送代码到 GitHub `master` 分支后，GitHub Actions 自动构建 Docker 镜像，推送到 GitHub Container Registry，然后由自托管 GitHub Runner 在服务器上拉取镜像并用 Docker Compose 启动服务。

本文不会把真实密码、密钥、Token 写入仓库。所有敏感值都用 `<请替换>` 表示。

## 一、部署架构

生产环境包含这些组件：

| 组件 | 作用 | 当前项目配置 |
| --- | --- | --- |
| GitHub 仓库 | 开发主线、触发自动部署 | `https://github.com/1971697432/weblog.git` |
| GitHub Actions | 构建镜像、执行部署 | `.github/workflows/deploy.yml` |
| GHCR | 存放 Docker 镜像 | `ghcr.io/1971697432/weblog/*` |
| 自托管 Runner | 在服务器上执行部署命令 | 标签：`self-hosted, prod, aws-tokyo` |
| Docker Compose | 编排生产容器 | `docker-compose.prod.yml` |
| Nginx | HTTPS、反向代理、静态管理端 | `nginx/nginx.conf` |
| MySQL | 数据库 | MySQL 8.0.36 |
| Redis | 缓存、限流、验证码状态 | Redis 7.2 Alpine |
| 后端 API | Spring Boot 服务 | 容器内端口 `9091` |
| 用户端 | Nuxt SSR 用户站点 | 容器内端口 `3000` |
| 管理端 | Nuxt SPA 管理后台 | 由 Nginx 以 `/admin` 路径提供 |
| Cloudflare | DNS、CDN、SSL、WAF、R2 对象存储 | 域名示例：`zhhhkl.top` |

访问路径：

| 地址 | 说明 |
| --- | --- |
| `https://zhhhkl.top/` | 用户端博客首页 |
| `https://zhhhkl.top/admin` | 管理后台 |
| `https://zhhhkl.top/api/...` | 后端 API |
| `https://www.zhhhkl.top/...` | 自动 301 到主域 `https://zhhhkl.top/...` |

## 二、部署前准备

你需要准备：

1. 一台 Linux 服务器，推荐 Ubuntu 22.04 LTS 或 Ubuntu 24.04 LTS。
2. 一个域名，本文以当前项目配置的 `zhhhkl.top` 为例。
3. 一个 GitHub 账号，并拥有仓库 `1971697432/weblog` 的管理权限。
4. 一个 Cloudflare 账号，用于托管 DNS、配置 SSL、可选配置 R2。
5. 服务器开放公网 `80` 和 `443` 端口。

推荐服务器最低配置：

| 资源 | 推荐值 |
| --- | --- |
| CPU | 2 核或以上 |
| 内存 | 2GB 起步，4GB 更稳 |
| 磁盘 | 40GB 起步 |
| 系统 | Ubuntu 22.04 LTS |

当前 `docker-compose.prod.yml` 已经给容器设置了较低资源上限，适合小型博客部署。

## 三、服务器初始化

以下命令默认你已经通过 SSH 登录服务器。

### 1. 更新系统

```bash
sudo apt update
sudo apt upgrade -y
```

### 2. 安装基础工具

```bash
sudo apt install -y ca-certificates curl gnupg git unzip vim ufw openssl
```

### 3. 安装 Docker

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

验证 Docker：

```bash
docker --version
docker compose version
sudo docker run --rm hello-world
```

### 4. 允许当前用户使用 Docker

```bash
sudo usermod -aG docker $USER
```

执行后退出 SSH 再重新登录。重新登录后验证：

```bash
docker ps
```

如果不报权限错误，说明成功。

### 5. 配置防火墙

只开放 SSH、HTTP、HTTPS：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

注意：生产 Compose 已经把 MySQL、Redis、API、SSR 端口绑定到 `127.0.0.1`，公网不能直接访问这些端口：

| 服务 | 绑定 |
| --- | --- |
| MySQL | `127.0.0.1:3306:3306` |
| Redis | `127.0.0.1:6379:6379` |
| 后端 API | `127.0.0.1:9091:9091` |
| 用户端 SSR | `127.0.0.1:3000:3000` |

### 6. 创建部署目录

当前 GitHub Actions 固定使用 `/opt/weblog`：

```bash
sudo mkdir -p /opt/weblog
sudo chown -R $USER:$USER /opt/weblog
mkdir -p /opt/weblog/nginx/ssl
mkdir -p /opt/weblog/admin-static
mkdir -p /opt/weblog/uploads
```

### 7. 克隆仓库到服务器

```bash
cd /opt/weblog
git clone https://github.com/1971697432/weblog.git repo-tmp
cp repo-tmp/docker-compose.prod.yml ./docker-compose.prod.yml
cp -r repo-tmp/nginx ./nginx
rm -rf repo-tmp
```

也可以把整个仓库直接放在 `/opt/weblog`，但当前自动部署工作流只要求 `/opt/weblog/docker-compose.prod.yml`、`/opt/weblog/nginx/`、`/opt/weblog/.env.prod` 存在。

## 四、Cloudflare DNS 配置

### 1. 把域名接入 Cloudflare

1. 登录 Cloudflare。
2. 点击 `Add a domain`。
3. 输入你的域名，例如 `zhhhkl.top`。
4. 选择免费套餐即可。
5. Cloudflare 会给出两个 Nameserver。
6. 去你的域名注册商后台，把域名 NS 修改为 Cloudflare 提供的 Nameserver。
7. 回到 Cloudflare 等待生效。

NS 生效可能需要几分钟到 24 小时。

### 2. 添加 DNS 记录

进入 Cloudflare 域名后台，打开 `DNS`，添加：

| 类型 | 名称 | 内容 | 代理状态 |
| --- | --- | --- | --- |
| A | `@` | `<你的服务器公网 IPv4>` | 开启代理，橙色云 |
| A | `www` | `<你的服务器公网 IPv4>` | 开启代理，橙色云 |

如果你的服务器有 IPv6，可以额外添加 AAAA 记录。

### 3. 配置 SSL/TLS 模式

进入 `SSL/TLS`：

1. `Overview` 中选择 `Full (strict)`。
2. 不建议选择 `Flexible`，否则容易出现循环重定向或 Cookie 安全问题。
3. 当前 Nginx 配置要求服务器本地有证书：
   - `/opt/weblog/nginx/ssl/fullchain.pem`
   - `/opt/weblog/nginx/ssl/privkey.pem`

## 五、申请 HTTPS 证书

当前 Nginx 配置读取：

```text
/etc/nginx/ssl/fullchain.pem
/etc/nginx/ssl/privkey.pem
```

在 Docker Compose 中映射为：

```yaml
./nginx/ssl:/etc/nginx/ssl:ro
```

所以服务器上实际文件路径是：

```text
/opt/weblog/nginx/ssl/fullchain.pem
/opt/weblog/nginx/ssl/privkey.pem
```

推荐使用 Cloudflare Origin Certificate，配置简单，适合使用 Cloudflare 代理的站点。

### 方式 A：Cloudflare Origin Certificate

1. 进入 Cloudflare 控制台。
2. 打开 `SSL/TLS`。
3. 打开 `Origin Server`。
4. 点击 `Create Certificate`。
5. 选择 `Generate private key and CSR with Cloudflare`。
6. Hostnames 填：
   - `zhhhkl.top`
   - `*.zhhhkl.top`
7. 证书有效期可选 15 年。
8. 创建后复制证书和私钥。

在服务器写入证书：

```bash
mkdir -p /opt/weblog/nginx/ssl
vim /opt/weblog/nginx/ssl/fullchain.pem
```

粘贴 Cloudflare 提供的证书内容，保存。

再写入私钥：

```bash
vim /opt/weblog/nginx/ssl/privkey.pem
```

设置权限：

```bash
chmod 600 /opt/weblog/nginx/ssl/privkey.pem
chmod 644 /opt/weblog/nginx/ssl/fullchain.pem
```

### 方式 B：Let's Encrypt

如果不用 Cloudflare Origin Certificate，也可以用 Let's Encrypt。但当前 `docker-compose.prod.yml` 没有内置 certbot 容器，也没有挂载 ACME challenge 目录；要使用 Let's Encrypt，需要额外补齐证书申请、续期、目录挂载和 Nginx reload 流程。新手建议优先用方式 A。

## 六、Cloudflare 安全和缓存设置

### 1. 开启 Always Use HTTPS

进入 `SSL/TLS` -> `Edge Certificates`：

1. 打开 `Always Use HTTPS`。
2. 打开 `Automatic HTTPS Rewrites`。

### 2. 配置 WAF 基础规则

进入 `Security` -> `WAF`，建议打开托管规则集。免费版至少可以使用基础安全等级。

推荐设置：

| 项 | 推荐值 |
| --- | --- |
| Security Level | Medium |
| Bot Fight Mode | 可开启 |
| Browser Integrity Check | 开启 |

注意：如果开启了过强的 Bot 或 Challenge，可能影响 Nuxt SSR 服务端请求。因此当前项目已经配置：

```yaml
NUXT_API_INTERNAL_BASE=http://weblog-api:9091
```

SSR 服务器内部请求会直接走 Docker 内网，不经过 Cloudflare。

### 3. 缓存规则建议

不要缓存 API。当前 Nginx 对 `/api/` 已加：

```nginx
add_header Cache-Control "no-store, no-cache, must-revalidate";
```

Cloudflare 页面规则或缓存规则建议：

| 路径 | 建议 |
| --- | --- |
| `zhhhkl.top/api/*` | Bypass Cache |
| `zhhhkl.top/admin*` | Bypass Cache 或 Respect Existing Headers |
| `zhhhkl.top/_nuxt/*` | Cache Everything，Edge TTL 可设 1 月 |
| `zhhhkl.top/uploads/*` | 可缓存，当前 Nginx 已设置 30 天 |

如果你不懂缓存规则，先不配置也能运行，Cloudflare 默认行为通常不会缓存动态 HTML 和 API。

## 七、Cloudflare R2 配置（可选但推荐）

项目支持三种存储：

| 值 | 说明 |
| --- | --- |
| `local` | 本地上传到服务器 `/opt/weblog/uploads` |
| `aliyun-oss` | 阿里云 OSS |
| `r2` | Cloudflare R2 |

如果你想使用 Cloudflare R2，按下面做。

### 1. 创建 R2 Bucket

1. 进入 Cloudflare 控制台。
2. 打开 `R2 Object Storage`。
3. 点击 `Create bucket`。
4. Bucket 名称示例：`weblog-prod`。
5. 位置保持默认即可。

### 2. 创建 R2 API Token

1. 进入 `R2 Object Storage`。
2. 打开 `Manage R2 API Tokens`。
3. 点击 `Create API token`。
4. 权限选择 `Object Read & Write`。
5. Bucket 选择刚创建的 `weblog-prod`。
6. 创建后记录：
   - Access Key ID
   - Secret Access Key
   - Account ID

### 3. 找到 R2 Endpoint

R2 Endpoint 格式通常是：

```text
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

例如：

```text
https://1234567890abcdef.r2.cloudflarestorage.com
```

### 4. 配置 R2 公共访问域名

建议给 R2 绑定自定义域名，例如：

```text
media.zhhhkl.top
```

步骤：

1. 进入 R2 Bucket。
2. 打开 `Settings`。
3. 找到 `Custom Domains`。
4. 添加 `media.zhhhkl.top`。
5. 按 Cloudflare 提示自动创建 DNS。

配置成功后，生产环境变量填写：

```text
STORAGE_PROVIDER=r2
R2_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=<你的 R2 Access Key ID>
R2_ACCESS_KEY_SECRET=<你的 R2 Secret Access Key>
R2_BUCKET_NAME=weblog-prod
R2_PUBLIC_BASE_URL=https://media.zhhhkl.top
R2_REGION=auto
UPLOAD_BASE_URL=https://media.zhhhkl.top
```

如果你暂时不使用 R2，使用本地上传即可：

```text
STORAGE_PROVIDER=local
UPLOAD_BASE_URL=https://zhhhkl.top/uploads
```

## 八、GitHub 自动部署配置

### 1. 工作流说明

当前自动部署文件：

```text
.github/workflows/deploy.yml
```

触发条件：

```yaml
on:
  push:
    branches:
      - master
  workflow_dispatch:
```

也就是说：

1. 推送到 GitHub `master` 分支会自动部署。
2. 也可以在 GitHub Actions 页面手动触发。

构建产物：

| Job | 镜像 |
| --- | --- |
| `build-and-push-backend` | `ghcr.io/1971697432/weblog/weblog-api:latest` |
| `build-and-push-user` | `ghcr.io/1971697432/weblog/weblog-user:latest` |
| `build-and-push-admin` | `ghcr.io/1971697432/weblog/weblog-admin:latest` |

部署 Job：

```yaml
deploy-to-server:
  runs-on: [self-hosted, prod, aws-tokyo]
```

所以服务器上必须安装并运行一个带这三个标签的 GitHub self-hosted runner。

### 2. 配置仓库 Packages 权限

进入 GitHub 仓库：

```text
Settings -> Actions -> General
```

确认：

1. `Actions permissions` 允许运行工作流。
2. `Workflow permissions` 选择 `Read and write permissions`。
3. 勾选 `Allow GitHub Actions to create and approve pull requests` 可不选，本部署不需要。

工作流里已经声明：

```yaml
permissions:
  contents: read
  packages: write
```

这样 `GITHUB_TOKEN` 可以推送镜像到 GHCR。

### 3. 安装 GitHub Self-hosted Runner

进入 GitHub 仓库：

```text
Settings -> Actions -> Runners -> New self-hosted runner
```

选择 Linux x64，然后 GitHub 会给出一组命令。示例：

```bash
cd /opt
mkdir actions-runner && cd actions-runner
curl -o actions-runner-linux-x64.tar.gz -L https://github.com/actions/runner/releases/download/v<版本号>/actions-runner-linux-x64-<版本号>.tar.gz
tar xzf ./actions-runner-linux-x64.tar.gz
./config.sh --url https://github.com/1971697432/weblog --token <GitHub 页面给你的 Runner Token> --labels self-hosted,prod,aws-tokyo
```

注意 labels 必须包含：

```text
self-hosted,prod,aws-tokyo
```

否则部署 Job 找不到 Runner。

安装为系统服务：

```bash
sudo ./svc.sh install
sudo ./svc.sh start
sudo ./svc.sh status
```

### 4. 让 Runner 用户可以执行 Docker

如果 Runner 服务使用当前用户运行，执行：

```bash
sudo usermod -aG docker $USER
sudo systemctl restart actions.runner.*
```

验证：

```bash
docker ps
```

如果 Runner 日志中出现 Docker 权限错误，通常就是这一步没做好。

### 5. 登录 GHCR 拉取私有镜像

部署工作流中已经使用 `docker/login-action@v4` 登录 GHCR。通常不需要手动登录。

如果你要在服务器手动拉镜像，需要创建 GitHub Personal Access Token，权限至少包含：

```text
read:packages
```

登录命令：

```bash
echo "<GitHub PAT>" | docker login ghcr.io -u 1971697432 --password-stdin
```

## 九、配置 GitHub Secrets

进入 GitHub 仓库：

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

逐个添加下面这些 Secrets。

### 1. 数据库和 Redis

| Secret | 示例 | 说明 |
| --- | --- | --- |
| `ENV_PROD_MYSQL_ROOT` | `<强密码>` | MySQL root 密码 |
| `ENV_PROD_DB_USERNAME` | `blog_user` | 应用数据库用户 |
| `ENV_PROD_DB_PASSWORD` | `<强密码>` | 应用数据库密码 |
| `ENV_PROD_REDIS_PASSWORD` | `<强密码>` | Redis 密码 |

生成强密码示例：

```bash
openssl rand -base64 32
```

### 2. 应用安全密钥

| Secret | 示例 | 说明 |
| --- | --- | --- |
| `ENV_PROD_JWT_SECRET` | `<openssl rand -base64 64>` | Sa-Token JWT 密钥 |
| `ENV_PROD_CAPTCHA_SECRET_KEY` | `<openssl rand -base64 64>` | 滑块验证码 HMAC 密钥 |

生成命令：

```bash
openssl rand -base64 64
```

### 3. 域名和来源

| Secret | 当前项目示例 | 说明 |
| --- | --- | --- |
| `ENV_PROD_CORS_ORIGINS` | `https://zhhhkl.top,https://www.zhhhkl.top` | CORS 允许来源 |
| `ENV_PROD_ALLOWED_ORIGINS` | `https://zhhhkl.top,https://www.zhhhkl.top` | 安全校验允许来源 |
| `ENV_PROD_API_BASE_URL` | `https://zhhhkl.top` | 前端浏览器访问 API 的站点根地址，不带 `/api` |
| `ENV_PROD_UPLOAD_BASE_URL` | `https://zhhhkl.top/uploads` | 本地上传访问地址 |
| `ENV_PROD_OUTBOUND_LINK_ALLOWED_DOMAINS` | `zhhhkl.top,www.zhhhkl.top,github.com,gitee.com` | 允许的外链域名白名单 |

注意：`ENV_PROD_API_BASE_URL` 只写协议和域名，不要写成 `https://zhhhkl.top/api`。Compose 会自动拼接 `/api`：

```yaml
NUXT_PUBLIC_API_BASE=${API_BASE_URL}/api
```

### 4. 存储配置

如果用本地上传：

| Secret | 值 |
| --- | --- |
| `ENV_PROD_STORAGE_PROVIDER` | `local` |
| `ENV_PROD_UPLOAD_BASE_URL` | `https://zhhhkl.top/uploads` |

如果不用 OSS，可以不创建下面这些 Secret；当前 GitHub Actions 会把未配置的 Secret 当作空值写入 `.env.prod`。如果你希望 Secrets 列表更完整，也可以创建占位值，但不要填入无关真实密钥。

| Secret | 值 |
| --- | --- |
| `ENV_PROD_OSS_ENDPOINT` | 空 |
| `ENV_PROD_OSS_ACCESS_KEY_ID` | 空 |
| `ENV_PROD_OSS_ACCESS_KEY_SECRET` | 空 |
| `ENV_PROD_OSS_BUCKET_NAME` | 空 |
| `ENV_PROD_OSS_CDN_DOMAIN` | 空 |
| `ENV_PROD_OSS_CONTENT_MODERATION_ENABLED` | `false` |

如果用 Cloudflare R2：

| Secret | 示例 |
| --- | --- |
| `ENV_PROD_STORAGE_PROVIDER` | `r2` |
| `ENV_PROD_R2_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `ENV_PROD_R2_ACCESS_KEY_ID` | `<R2 Access Key ID>` |
| `ENV_PROD_R2_ACCESS_KEY_SECRET` | `<R2 Secret Access Key>` |
| `ENV_PROD_R2_BUCKET_NAME` | `weblog-prod` |
| `ENV_PROD_R2_PUBLIC_BASE_URL` | `https://media.zhhhkl.top` |
| `ENV_PROD_R2_REGION` | `auto` |
| `ENV_PROD_UPLOAD_BASE_URL` | `https://media.zhhhkl.top` |

### 5. 导入接口 Token

| Secret | 示例 | 说明 |
| --- | --- | --- |
| `ENV_PROD_IMPORT_TOKEN` | `<强随机字符串>` | 爬虫导入接口鉴权 Token |

生成：

```bash
openssl rand -hex 32
```

### 6. GitHub OAuth

如果要开启 GitHub 登录，需要创建 GitHub OAuth App。

进入 GitHub：

```text
Settings -> Developer settings -> OAuth Apps -> New OAuth App
```

填写：

| 项 | 值 |
| --- | --- |
| Application name | `Weblog` |
| Homepage URL | `https://zhhhkl.top` |
| Authorization callback URL | `https://zhhhkl.top/api/oauth/github/callback` |

创建后得到：

| Secret | 值 |
| --- | --- |
| `ENV_PROD_GITHUB_CLIENT_ID` | GitHub OAuth Client ID |
| `ENV_PROD_GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret |

如果暂时不用 GitHub 登录，可以先留空。

### 7. 邮件配置

验证码登录、注册、重置密码需要 SMTP。

| Secret | 示例 |
| --- | --- |
| `ENV_PROD_MAIL_SMTP_HOST` | `smtp.qq.com` |
| `ENV_PROD_MAIL_SMTP_PORT` | `465` |
| `ENV_PROD_MAIL_USERNAME` | `your@qq.com` |
| `ENV_PROD_MAIL_PASSWORD` | `<邮箱授权码，不是登录密码>` |
| `ENV_PROD_MAIL_SSL_ENABLED` | `true` |
| `ENV_PROD_MAIL_FROM_NAME` | `Weblog` |
| `ENV_PROD_MAIL_CODE_EXPIRE_MINUTES` | `10` |

常见 SMTP：

| 邮箱 | Host | Port | SSL |
| --- | --- | --- | --- |
| QQ 邮箱 | `smtp.qq.com` | `465` | `true` |
| 163 邮箱 | `smtp.163.com` | `465` | `true` |
| Gmail | `smtp.gmail.com` | `465` | `true` |

注意：很多邮箱要求使用“授权码”，不是邮箱登录密码。

## 十、首次部署前服务器文件检查

部署前确认这些文件存在：

```bash
ls -l /opt/weblog/docker-compose.prod.yml
ls -l /opt/weblog/nginx/nginx.conf
ls -l /opt/weblog/nginx/ssl/fullchain.pem
ls -l /opt/weblog/nginx/ssl/privkey.pem
```

确认目录存在：

```bash
ls -ld /opt/weblog/admin-static
ls -ld /opt/weblog/uploads
```

如果还没有 `.env.prod`，不用手动创建。自动部署时 GitHub Actions 会根据 Secrets 在服务器生成：

```text
/opt/weblog/.env.prod
```

如果你要手动测试，也可以自己创建 `/opt/weblog/.env.prod`。

## 十一、首次触发自动部署

### 1. 推送代码到 GitHub

在本地项目根目录：

```bash
git status
git add .
git commit -m "chore: update deployment config"
git push github master
```

如果只想触发部署，没有代码改动，可以在 GitHub 页面手动触发。

### 2. 手动触发 GitHub Actions

进入 GitHub 仓库：

```text
Actions -> Build & Deploy to Aliyun -> Run workflow
```

选择 `master`，点击运行。

### 3. 查看构建步骤

进入运行中的 workflow，应该看到这些 Job：

```text
build-and-push-backend
build-and-push-user
build-and-push-admin
prepare-artifacts
```

前三个 Job 会构建镜像并推送到 GHCR。

`deploy-to-server` 会在服务器执行：

1. 下载部署产物。
2. 写入 `/opt/weblog/.env.prod`。
3. 登录 GHCR。
4. 拉取三个应用镜像。
5. 拉取 MySQL、Redis、Nginx 基础镜像。
6. 从管理端镜像提取静态文件到 `/opt/weblog/admin-static`。
7. 启动 Docker Compose。
8. 检查 SSR 到后端 API 的内网链路。

## 十二、部署后验证

### 1. 在服务器查看容器

```bash
cd /opt/weblog
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

正常应该看到：

```text
weblog-mysql
weblog-redis
weblog-api
weblog-user
weblog-nginx
```

### 2. 查看后端健康检查

服务器本机执行：

```bash
curl http://127.0.0.1:9091/actuator/health/liveness
```

正常返回类似：

```json
{"status":"UP"}
```

### 3. 检查用户端

```bash
curl -I https://zhhhkl.top/
```

应该看到 `200` 或 `3xx` 后最终访问正常。

浏览器打开：

```text
https://zhhhkl.top/
```

### 4. 检查管理端

浏览器打开：

```text
https://zhhhkl.top/admin
```

如果刷新 `/admin/login` 或 `/admin/post/create` 这类子路由仍能打开，说明 SPA 回退配置正常。

### 5. 检查 API

```bash
curl -I https://zhhhkl.top/api/portal/post/recent?pageNum=1\&pageSize=1
```

正常应该返回 `200`。

### 6. 检查 Docker 日志

```bash
docker logs --tail=200 weblog-api
docker logs --tail=200 weblog-user
docker logs --tail=200 weblog-nginx
```

## 十三、手动部署和回滚

正常情况下使用 GitHub Actions 自动部署即可。下面是手动命令，方便排查。

### 1. 手动拉取镜像

```bash
docker pull ghcr.io/1971697432/weblog/weblog-api:latest
docker pull ghcr.io/1971697432/weblog/weblog-user:latest
docker pull ghcr.io/1971697432/weblog/weblog-admin:latest
```

### 2. 手动提取管理端静态文件

```bash
cd /opt/weblog
docker rm -f admin-temp >/dev/null 2>&1 || true
rm -rf /opt/weblog/admin-static/*
docker create --name admin-temp ghcr.io/1971697432/weblog/weblog-admin:latest
docker cp admin-temp:/usr/share/nginx/html/admin/. /opt/weblog/admin-static
docker rm admin-temp
```

确认：

```bash
ls -l /opt/weblog/admin-static/index.html
```

### 3. 启动生产服务

```bash
cd /opt/weblog
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --remove-orphans
```

### 4. 停止生产服务

```bash
cd /opt/weblog
docker compose --env-file .env.prod -f docker-compose.prod.yml down
```

### 5. 回滚思路

当前镜像标签使用 `latest`，如果要精确回滚，建议后续把工作流改成同时推送提交 SHA 标签，例如：

```text
ghcr.io/1971697432/weblog/weblog-api:<commit-sha>
```

当前简单回滚方式：

1. 在 GitHub 回退到上一个稳定提交。
2. 推送到 `master`。
3. 重新运行部署工作流。

## 十四、数据库初始化和迁移

当前后端生产环境启用 Flyway：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

迁移脚本路径：

```text
weblog-backend/weblog-api/src/main/resources/db/migration/
```

第一次启动时 Flyway 会执行：

```text
V1__schema.sql
V2__data.sql
V3__...
...
```

重要规则：

1. 生产环境不要手动导入 `database/weblog.sql` 后再启用 Flyway，否则可能重复建表或重复字段。
2. 如果使用 `docker-compose.prod.yml` 第一次启动，让后端自动通过 Flyway 初始化数据库即可。
3. 如果你已经手动导入完整 SQL，需要谨慎处理 Flyway baseline，不建议新手这么做。
4. 当前项目规则要求数据库结构变更必须同时更新：
   - Flyway 增量迁移脚本
   - `database/sql/init/02-schema.sql`
   - `database/weblog.sql`

## 十五、备份与恢复

生产环境上线后，至少要备份四类数据：MySQL 数据、上传文件、HTTPS 证书、生产环境变量。备份文件建议放到服务器以外的位置，例如对象存储、另一台服务器或本地加密硬盘。

### 1. 备份 MySQL

在服务器执行：

```bash
mkdir -p /opt/weblog/backups
docker exec weblog-mysql mysqldump -uroot -p'<MYSQL_ROOT_PASSWORD>' --single-transaction --routines --triggers weblog > /opt/weblog/backups/weblog-$(date +%F).sql
```

说明：

1. 把 `<MYSQL_ROOT_PASSWORD>` 替换为 `.env.prod` 中的真实 MySQL root 密码。
2. 备份文件不要提交到 Git。
3. 建议定期把 `/opt/weblog/backups/*.sql` 复制到服务器外部。

### 2. 恢复 MySQL

恢复前先确认目标数据库是否允许被覆盖。生产环境恢复必须先停业务或进入维护窗口。

```bash
docker exec -i weblog-mysql mysql -uroot -p'<MYSQL_ROOT_PASSWORD>' weblog < /opt/weblog/backups/weblog-YYYY-MM-DD.sql
```

如果你不确定当前数据是否还能保留，先再做一次备份，不要直接覆盖。

### 3. 备份上传文件

本地上传文件在：

```text
/opt/weblog/uploads
```

备份：

```bash
tar -czf /opt/weblog/backups/uploads-$(date +%F).tar.gz -C /opt/weblog uploads
```

恢复：

```bash
tar -xzf /opt/weblog/backups/uploads-YYYY-MM-DD.tar.gz -C /opt/weblog
chown -R 1000:1000 /opt/weblog/uploads
chmod -R u+rwX,go+rX /opt/weblog/uploads
```

如果生产使用 R2 或 OSS，上传文件主要在对象存储中，仍建议定期检查对象存储是否有版本控制、生命周期和误删保护。

### 4. 备份证书和环境变量

需要备份：

```text
/opt/weblog/nginx/ssl/fullchain.pem
/opt/weblog/nginx/ssl/privkey.pem
/opt/weblog/.env.prod
```

注意：`.env.prod`、`privkey.pem` 都是敏感文件，只能放在安全位置，不要发到聊天窗口，不要提交到 Git。

### 5. 定期检查备份可用性

备份不是生成文件就结束。建议每次重要发布前确认：

```bash
ls -lh /opt/weblog/backups
```

至少每隔一段时间在测试环境做一次恢复演练，确认 SQL 文件和上传文件都能正常恢复。

## 十六、常见踩坑和解决办法

### 1. GitHub Actions 一直等待 Runner

现象：`deploy-to-server` 显示 queued，不执行。

原因：服务器 Runner 没在线，或标签不匹配。

解决：

```bash
cd /opt/actions-runner
sudo ./svc.sh status
sudo ./svc.sh start
```

确认 GitHub Runner 标签包含：

```text
self-hosted
prod
aws-tokyo
```

### 2. Runner 没有 Docker 权限

现象：日志出现 `permission denied while trying to connect to the Docker daemon socket`。

解决：

```bash
sudo usermod -aG docker <runner运行用户>
sudo systemctl restart actions.runner.*
```

### 3. GHCR 镜像拉取失败

现象：`docker pull ghcr.io/...` 失败。

检查：

1. GitHub Actions 是否成功构建并推送镜像。
2. 仓库 Packages 权限是否允许 Actions 写入。
3. `deploy-to-server` 中是否完成 `Login to Container Registry`。

手动验证：

```bash
docker pull ghcr.io/1971697432/weblog/weblog-api:latest
```

### 4. Cloudflare 显示 521 或 522

含义：Cloudflare 连接不到源站。

检查：

1. 服务器安全组是否开放 `80` 和 `443`。
2. `ufw` 是否允许 `80/tcp` 和 `443/tcp`。
3. Nginx 容器是否运行：

```bash
docker ps | grep weblog-nginx
docker logs --tail=100 weblog-nginx
```

### 5. HTTPS 证书错误

检查证书文件：

```bash
ls -l /opt/weblog/nginx/ssl/fullchain.pem
ls -l /opt/weblog/nginx/ssl/privkey.pem
```

检查 Nginx 配置：

```bash
docker exec weblog-nginx nginx -t
```

Cloudflare SSL 模式必须是：

```text
Full (strict)
```

如果你使用 Cloudflare Origin Certificate，不要关闭 Cloudflare 代理，否则浏览器会认为源站证书不受信任。

### 6. 网站无限重定向

常见原因：Cloudflare SSL 模式设置成了 `Flexible`。

解决：改为：

```text
SSL/TLS -> Overview -> Full (strict)
```

### 7. 管理端刷新 404

当前 Nginx 已配置：

```nginx
location /admin {
    alias /usr/share/nginx/html/admin;
    index index.html;
    try_files $uri $uri/ /admin/index.html;
}
```

如果仍 404，检查管理端静态文件是否提取成功：

```bash
ls -l /opt/weblog/admin-static/index.html
```

### 8. 用户端页面能打开，但接口失败

检查生产变量：

```bash
cat /opt/weblog/.env.prod | grep API_BASE_URL
```

应类似：

```text
API_BASE_URL=https://zhhhkl.top
API_INTERNAL_BASE_URL=http://weblog-api:9091
```

不要把 `API_BASE_URL` 写成 `https://zhhhkl.top/api`。

### 9. 登录后 Cookie 不生效

生产配置中后端 Cookie 是 `secure: true`，必须 HTTPS 访问。

检查：

1. 浏览器地址必须是 `https://zhhhkl.top`。
2. Cloudflare SSL 必须正常。
3. 不要用 `http://服务器IP` 测登录。

### 10. 邮箱验证码发不出去

检查：

1. SMTP Host、Port 是否正确。
2. 邮箱是否开启 SMTP 服务。
3. `MAIL_PASSWORD` 是否是授权码，不是邮箱登录密码。
4. 服务器云厂商是否封禁 25 端口。推荐使用 465 SSL。

查看后端日志：

```bash
docker logs --tail=200 weblog-api
```

### 11. MySQL 一直不健康

查看日志：

```bash
docker logs --tail=200 weblog-mysql
```

常见原因：

1. `.env.prod` 中 MySQL 密码为空。
2. 数据卷中已有旧初始化数据，和新密码不一致。
3. 服务器内存太小。

如果是测试环境可以清空数据卷重来，生产环境不要随便删数据卷。

### 12. Redis 连接失败

检查 `.env.prod`：

```bash
cat /opt/weblog/.env.prod | grep REDIS_PASSWORD
```

检查 Redis：

```bash
docker exec -it weblog-redis redis-cli -a '<REDIS_PASSWORD>' ping
```

正常返回：

```text
PONG
```

### 13. 上传图片 403

当前 Nginx 对 `/uploads/` 开启了 Referer 校验：

```nginx
valid_referers server_names;
if ($invalid_referer) { return 403; }
```

这表示直接从非本站页面引用上传文件会被拒绝。站内正常访问不应受影响。

如果你使用 R2，上传访问地址应改为 R2 公共域名，例如：

```text
UPLOAD_BASE_URL=https://media.zhhhkl.top
R2_PUBLIC_BASE_URL=https://media.zhhhkl.top
```

### 14. Cloudflare 缓存导致更新不生效

解决：

1. Cloudflare -> Caching -> Purge Cache。
2. 点击 `Purge Everything`。
3. 浏览器强刷：Windows `Ctrl + F5`，Mac `Cmd + Shift + R`。

### 15. Flyway 报重复字段或重复表

原因通常是你手动导入了完整 SQL，又让 Flyway 执行迁移。

新手推荐做法：

1. 新数据库不要手动导入 `database/weblog.sql`。
2. 让后端启动时自动执行 Flyway。
3. 如果已经误导入，先备份，再找熟悉数据库的人处理 baseline。

## 十七、日常发布流程

日常开发完成后：

```bash
git status
git add .
git commit -m "fix: describe your change"
git push github master
```

如果还需要镜像到 Gitee：

```bash
git push gitee master
```

GitHub 推送后自动部署，Gitee 只是镜像分发，不作为开发主线。

## 十八、上线检查清单

发布前检查：

| 检查项 | 命令或位置 |
| --- | --- |
| GitHub Secrets 已配置 | GitHub 仓库 Settings |
| Runner 在线 | GitHub Actions Runners |
| Runner 标签正确 | `self-hosted, prod, aws-tokyo` |
| 服务器 Docker 正常 | `docker ps` |
| 证书存在 | `/opt/weblog/nginx/ssl/*.pem` |
| 生产 Compose 存在 | `/opt/weblog/docker-compose.prod.yml` |
| Nginx 配置存在 | `/opt/weblog/nginx/nginx.conf` |
| Cloudflare DNS 指向服务器 | Cloudflare DNS 页面 |
| Cloudflare SSL 模式正确 | `Full (strict)` |
| 80/443 端口开放 | 云厂商安全组 + `ufw status` |

发布后检查：

| 检查项 | 命令或地址 |
| --- | --- |
| 容器状态 | `docker compose --env-file .env.prod -f docker-compose.prod.yml ps` |
| API 健康 | `curl http://127.0.0.1:9091/actuator/health/liveness` |
| 用户端 | `https://zhhhkl.top/` |
| 管理端 | `https://zhhhkl.top/admin` |
| API | `https://zhhhkl.top/api/portal/post/recent?pageNum=1&pageSize=1` |
| 后端日志 | `docker logs --tail=200 weblog-api` |
| Nginx 日志 | `docker logs --tail=200 weblog-nginx` |

## 十九、当前项目真实部署值速查

| 项 | 当前值 |
| --- | --- |
| GitHub 主仓库 | `https://github.com/1971697432/weblog.git` |
| Gitee 镜像仓库 | `https://gitee.com/chuan123321/weblog.git` |
| 生产分支 | `master` |
| 部署工作流 | `.github/workflows/deploy.yml` |
| 生产 Compose | `docker-compose.prod.yml` |
| Nginx 配置 | `nginx/nginx.conf` |
| 服务器部署目录 | `/opt/weblog` |
| 主域名 | `zhhhkl.top` |
| www 域名 | `www.zhhhkl.top` |
| 管理端路径 | `/admin` |
| API 路径 | `/api` |
| 后端镜像 | `ghcr.io/1971697432/weblog/weblog-api:latest` |
| 用户端镜像 | `ghcr.io/1971697432/weblog/weblog-user:latest` |
| 管理端镜像 | `ghcr.io/1971697432/weblog/weblog-admin:latest` |
| 后端容器 | `weblog-api` |
| 用户端容器 | `weblog-user` |
| Nginx 容器 | `weblog-nginx` |
| MySQL 容器 | `weblog-mysql` |
| Redis 容器 | `weblog-redis` |
| 后端端口 | `9091` |
| 用户端 SSR 端口 | `3000` |
| MySQL 端口 | `3306`，仅本机绑定 |
| Redis 端口 | `6379`，仅本机绑定 |
| Runner 标签 | `self-hosted, prod, aws-tokyo` |

## 二十、不要做的事

1. 不要把 `.env.prod` 提交到 Git。
2. 不要把 GitHub Token、R2 Secret、数据库密码写进 Markdown 文档。
3. 不要把 Cloudflare SSL 设置成 `Flexible`。
4. 不要把 `API_BASE_URL` 写成带 `/api` 的地址。
5. 不要对生产数据库随便执行 `docker compose down -v`，这会删除数据卷。
6. 不要让 MySQL、Redis 直接暴露到公网。
7. 不要手动导入完整 SQL 后又让 Flyway 自动迁移，除非你明确知道如何 baseline。
8. 不要随意修改 Runner 标签，工作流依赖这些标签。

## 二十一、最小可用部署路径

如果你只想快速跑起来，按这个顺序做：

1. 买服务器，安装 Docker。
2. 把域名 `zhhhkl.top` 接入 Cloudflare。
3. DNS 添加 `@` 和 `www` 到服务器 IP。
4. Cloudflare SSL 设置 `Full (strict)`。
5. 创建 Cloudflare Origin Certificate，保存到 `/opt/weblog/nginx/ssl/`。
6. 在服务器创建 `/opt/weblog`，放入 `docker-compose.prod.yml` 和 `nginx/`。
7. 在 GitHub 配好所有 `ENV_PROD_*` Secrets。
8. 在服务器安装 GitHub self-hosted runner，标签设置为 `self-hosted,prod,aws-tokyo`。
9. 推送代码到 GitHub `master`。
10. 打开 GitHub Actions 看部署是否成功。
11. 浏览器访问 `https://zhhhkl.top/` 和 `https://zhhhkl.top/admin`。

完成以上步骤后，后续每次更新代码只需要：

```bash
git push github master
```

系统就会自动构建并部署。
