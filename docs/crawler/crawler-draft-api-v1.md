# Crawler Draft Integration API v1

Version: `v1`

## Overview

This contract defines the integration between local crawler worker and weblog backend for:

- Candidate ingestion
- Draft push result reporting
- Optional push callback status updates

All APIs are under `/api/admin/crawler/v1` and require integration token + device identifier headers.

## Authentication Headers

- `X-Crawler-Token`: integration token issued by admin
- `X-Crawler-Device-Id`: stable worker device identifier
- `X-Crawler-Request-Id`: optional trace id

## Idempotency

- Batch-level idempotency: `idempotencyKey`
- Item-level idempotency: `itemIdempotencyKey`
- Push-level idempotency: `pushIdempotencyKey`

Conflict behavior:

- Replayed request with same idempotency key returns previously persisted result.
- Different payload with same idempotency key returns conflict error.

## Endpoint: Candidate Ingestion

- Method: `POST`
- Path: `/api/admin/crawler/v1/candidates:ingest`

### Request

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
      "title": "sample",
      "summary": "sample summary",
      "contentMarkdown": "# sample",
      "coverImage": "file:///staging/img-1.jpg",
      "imageRefs": ["file:///staging/img-1.jpg"],
      "contentFingerprint": "sha256:...",
      "publishedAt": "2026-04-20T09:00:00",
      "author": "author",
      "metadata": {
        "extractor": "trafilatura",
        "fallbackUsed": false
      }
    }
  ]
}
```

### Response

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

## Endpoint: Draft Push Result Upsert

- Method: `POST`
- Path: `/api/admin/crawler/v1/push-results:upsert`

### Request

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

### Response

```json
{
  "pushRecordId": 501,
  "status": "succeeded",
  "targetDraftId": 8801
}
```

## Endpoint: Push Callback Status (Optional)

- Method: `POST`
- Path: `/api/admin/crawler/v1/push-callback`

### Request

```json
{
  "pushIdempotencyKey": "push:device-a:candidate-1001:v1",
  "state": "succeeded",
  "errorCode": "",
  "errorMessage": "",
  "updatedAt": "2026-04-21T14:31:00"
}
```

### Response

```json
{
  "ack": true
}

## Endpoint: Asset Upload (Two-Phase Media)

- Method: `POST`
- Path: `/api/admin/crawler/v1/assets:upload`
- Content-Type: `multipart/form-data`

### Request

- form field: `file` (image binary)

### Response

```json
{
  "url": "https://cdn.example.com/uploads/images/2026/04/abc.webp",
  "objectKey": "images/2026/04/abc.webp"
}
```
```

## Enumerations

- Candidate state: `review_pending`, `approved`, `rejected`, `failed`, `pushed`
- Push record state: `pending`, `processing`, `succeeded`, `failed`
