# 爬虫草稿集成 API v1

版本：`v1`

## 概述

本契约定义了本地爬虫工作器与 weblog 后端之间的集成接口，包括：

- 候选内容录入
- 草稿推送结果上报
- 可选的推送回调状态更新

所有接口均位于 `/api/admin/crawler/v1` 路径下，需要提供集成令牌和设备标识请求头。

## 认证请求头

- `X-Crawler-Token`：管理员签发的集成令牌
- `X-Crawler-Device-Id`：工作器的稳定设备标识
- `X-Crawler-Request-Id`：可选的链路追踪 ID

## 幂等性

- 批次级幂等：`idempotencyKey`
- 条目级幂等：`itemIdempotencyKey`
- 推送级幂等：`pushIdempotencyKey`

冲突处理策略：

- 相同幂等键的重复请求返回之前已持久化的结果。
- 相同幂等键但负载不同返回冲突错误。

## 接口：候选内容录入

- 方法：`POST`
- 路径：`/api/admin/crawler/v1/candidates:ingest`

### 请求

```json
{
  "idempotencyKey": "ingest:device-a:run-20260421-001",
  "workerRunId": "run-20260421-001",
  "submittedAt": "2026-04-21T14:20:00",
  "items": [
    {
      "itemIdempotencyKey": "candidate:device-a:sha256-xxx",
      "externalUrl": "https://example.com/post/123",
      "normalizedUrl": "https://example.com/post/123",
      "sourceSite": "example.com",
      "title": "示例标题",
      "summary": "示例摘要",
      "contentMarkdown": "# 示例内容",
      "coverImage": "file:///staging/img-1.jpg",
      "imageRefs": ["file:///staging/img-1.jpg"],
      "contentFingerprint": "sha256:...",
      "publishedAt": "2026-04-20T09:00:00",
      "author": "作者",
      "metadata": {
        "extractor": "trafilatura",
        "fallbackUsed": false
      }
    }
  ]
}
```

### 响应

```json
{
  "ingestRequestId": "ig-20260421-001",
  "accepted": [
    {
      "itemIdempotencyKey": "candidate:device-a:sha256-xxx",
      "candidateId": 1001,
      "state": "review_pending"
    }
  ],
  "rejected": [
    {
      "itemIdempotencyKey": "candidate:device-a:sha256-yyy",
      "reasonCode": "duplicate_content",
      "reasonMessage": "content fingerprint exists"
    }
  ]
}
```

## 接口：草稿推送结果更新

- 方法：`POST`
- 路径：`/api/admin/crawler/v1/push-results:upsert`

### 请求

```json
{
  "pushIdempotencyKey": "push:device-a:candidate-1001:v1",
  "candidateId": 1001,
  "targetDraftId": 8801,
  "status": "succeeded",
  "message": "draft created",
  "pushedAt": "2026-04-21T14:30:00"
}
```

### 响应

```json
{
  "pushRecordId": 501,
  "status": "succeeded",
  "targetDraftId": 8801
}
```

## 接口：推送回调状态（可选）

- 方法：`POST`
- 路径：`/api/admin/crawler/v1/push-callback`

### 请求

```json
{
  "pushIdempotencyKey": "push:device-a:candidate-1001:v1",
  "state": "succeeded",
  "errorCode": "",
  "errorMessage": "",
  "updatedAt": "2026-04-21T14:31:00"
}
```

### 响应

```json
{
  "ack": true
}
```

## 接口：素材上传（两阶段媒体上传）

- 方法：`POST`
- 路径：`/api/admin/crawler/v1/assets:upload`
- Content-Type：`multipart/form-data`

### 请求

- 表单字段：`file`（图片二进制数据）

### 响应

```json
{
  "url": "https://cdn.example.com/uploads/images/2026/04/abc.webp",
  "objectKey": "images/2026/04/abc.webp"
}
```

## 枚举值

- 候选内容状态：`review_pending`（待审核）、`approved`（已通过）、`rejected`（已拒绝）、`failed`（失败）、`pushed`（已推送）
- 推送记录状态：`pending`（待处理）、`processing`（处理中）、`succeeded`（成功）、`failed`（失败）
