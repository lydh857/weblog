# 爬虫本地工作器运维手册

## 1. 安装

### 后端

```bash
mvn --% -f weblog-backend/pom.xml -pl weblog-api -am -Dmaven.test.skip=true package
```

在 `http://127.0.0.1:9091` 启动 API 服务。

### 本地工作器

```bash
cd crawler-worker
python -m venv .venv
. .venv/Scripts/activate
pip install -e .
uvicorn app.main:app --host 127.0.0.1 --port 17891
```

### 桌面端

```bash
cd crawler-desktop
pnpm install
pnpm dev
```

Tauri 开发模式：

```bash
pnpm tauri dev
```

## 2. 令牌配置

在后端系统配置中设置：

- `crawler_ingest_enabled = true`
- `crawler_integration_token = <强随机令牌>`
- `crawler_draft_owner_user_id = 1`（或指定专用运营账号）

在工作器环境变量（`crawler-worker/.env`）中配置：

```bash
CRAWLER_BACKEND_BASE_URL=http://127.0.0.1:9091
CRAWLER_BACKEND_CRAWLER_TOKEN=<相同令牌>
CRAWLER_BACKEND_DEVICE_ID=local-worker-01
CRAWLER_BACKEND_REQUEST_ORIGIN=http://localhost:3001
CRAWLER_URL_POLICY_ALLOW_PRIVATE=false
CRAWLER_URL_POLICY_ALLOWLIST=
```

`CRAWLER_URL_POLICY_ALLOWLIST` 支持逗号分隔的 `主机名/IP/CIDR`，例如：

```bash
CRAWLER_URL_POLICY_ALLOWLIST=localhost,127.0.0.1,10.0.0.0/8
```

## 3. 操作流程

1. 从桌面端创建快速任务或批量任务。
2. 等待候选内容进入 `review_pending` 状态。
3. 在候选池中执行通过/拒绝/重新爬取操作。
4. 推送已通过的条目。
5. 在管理后台文章列表中验证草稿记录。

## 4. 回滚控制

紧急停止：

- 在后端配置中设置 `crawler_ingest_enabled = false`。

该操作会立即阻断所有爬虫集成接口。

本地回滚：

- 停止工作器和桌面端进程。
- 将 `crawler-worker` 和 `crawler-desktop` 回退到上一个发布标签。

## 5. 故障排查

### 推送时返回 `401/403`

- 确认爬虫令牌请求头与后端配置一致。
- 检查设备 ID 请求头是否存在。
- 确保后端 `crawler_ingest_enabled` 已设为 `true`。

### 推送失败，提示上传错误

- 确认后端存储已启用。
- 检查图片 MIME 类型和大小限制。
- 验证本地缓存路径和文件可读性。

### 提示 `private or loopback address is blocked`

- 工作器默认拦截私有地址/回环地址/链路本地地址。
- 通过 `CRAWLER_URL_POLICY_ALLOWLIST` 添加可信的本地目标。
- 仅在本地调试时可设置 `CRAWLER_URL_POLICY_ALLOW_PRIVATE=true`。

### 本地资源占用过高

- 将 `max_global_concurrency` 降低到 `2`。
- 保持 `max_domain_concurrency` 为 `1`。
- 减少站点配置中的批量大小。

### 去重拒绝过于频繁

- 检查归一化 URL 和指纹生成日志。
- 针对目标域名调整源 URL 归一化策略。
