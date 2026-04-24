# R2 防滥用与成本护栏（生产标准）

本文档对应线上公开站点的标准防护基线，目标是：

- 防止恶意刷上传、刷读取导致免费额度耗尽
- 提前告警并自动限流，避免失控扣费
- 保持 local / aliyun-oss / r2 可切换

## 1. 入口防刷（Cloudflare + 网关）

### 1.1 Cloudflare WAF 规则（必配）

在 Cloudflare Dashboard 为以下路径创建托管规则：

- `/api/admin/upload/*`
- `/api/portal/upload/*`
- `/api/admin/upload/sign`
- `/api/admin/upload/sign/verify`

建议动作：

- 非浏览器指纹 / 高风险 AS / 异常 UA：`Managed Challenge`
- 突发高频请求：`Block`

### 1.2 Cloudflare Rate Limiting（必配）

建议初始阈值（按 IP）：

- `POST /api/admin/upload/image`：`12 req / min`
- `POST /api/portal/upload/image`：`8 req / min`
- `GET /api/admin/upload/sign`：`30 req / min`

超阈值动作：`Managed Challenge` 或 `Block 10 min`

### 1.3 Nginx 网关限流（已在代码实现）

仓库已对上传接口增加独立限流区：

- `upload_limit`（上传）
- `media_limit`（媒体资源读取）

相关文件：`nginx/nginx.conf`

## 2. 应用层配额（用户/IP/全站）

已实现 Redis 原子配额（次数 + 字节）：

- 用户维度（日）
- IP 维度（日）
- 全站维度（日、月）

相关代码：

- `weblog-backend/weblog-api/src/main/java/com/blog/api/security/UploadGuardService.java`
- `weblog-backend/weblog-api/src/main/java/com/blog/api/security/UploadGuardProperties.java`

配置项（可用环境变量覆盖）：

- `blog.security.upload-guard.*`
- 见 `weblog-backend/weblog-api/src/main/resources/application-prod.yml`

## 3. 存储权限（R2 最小权限）

### 3.1 Bucket 权限

- Bucket 默认私有（不要公开列目录）
- Access Key 仅赋予该 Bucket 的最小读写权限
- 分离生产与测试账号/Key

### 3.2 访问域名

- 通过自定义域名访问（`R2_PUBLIC_BASE_URL`）
- 禁止直接暴露管理接口到公网

## 4. 预算护栏（告警 + 阈值）

### 4.1 Cloudflare Billing 告警（必配）

建议告警阈值：

- 免费额度 50%
- 免费额度 80%
- 免费额度 95%

### 4.2 站点内部硬阈值（已实现）

通过上传配额全站月度阈值硬拦截，避免继续写入导致扩容扣费。

## 5. 生命周期与垃圾回收

### 5.1 临时文件清理（已实现）

- 定时任务每天清理 `temp/` 历史对象
- 相关：`TempFileCleanupScheduler`

### 5.2 R2 生命周期规则（建议）

在 R2 控制台配置：

- `temp/*` 1~3 天自动过期
- 未完成分片上传自动清理

## 6. 防盗链与缓存策略

### 6.1 Nginx 防盗链（本地 uploads，已实现）

- `/uploads/` 仅允许本站 Referer
- 非法 Referer 返回 `403`

### 6.2 Cloudflare 热链保护（R2 场景，必配）

- 开启 Hotlink Protection 或自定义 WAF Referer 规则
- 图片域名开启缓存，降低回源和读操作成本

## 7. 多后端可迁移开关

已支持存储后端切换：

- `STORAGE_PROVIDER=local`
- `STORAGE_PROVIDER=aliyun-oss`
- `STORAGE_PROVIDER=r2`

配套配置：

- 阿里 OSS：`OSS_*`
- Cloudflare R2：`R2_*`

关键文件：

- `StorageFacade`
- `R2StorageService`
- `OssService`
- `LocalFileService`

## 8. 上线前核对清单

1. Cloudflare WAF + Rate Limit 已启用并命中日志可见。
2. Billing 告警阈值已设置。
3. `STORAGE_PROVIDER` 与对应密钥已在生产 Secrets 配置。
4. 上传接口压测时可触发应用层配额拦截（HTTP 429）。
5. temp 对象可被定时任务清理。
6. 图片域名热链保护规则生效。
