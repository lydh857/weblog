# Crawler Local Worker Runbook

## 1. Installation

### Backend

```bash
mvn --% -f weblog-backend/pom.xml -pl weblog-api -am -Dmaven.test.skip=true package
```

Run API service on `http://127.0.0.1:9091`.

### Local Worker

```bash
cd crawler-worker
python -m venv .venv
. .venv/Scripts/activate
pip install -e .
uvicorn app.main:app --host 127.0.0.1 --port 17891
```

### Desktop

```bash
cd crawler-desktop
pnpm install
pnpm dev
```

Tauri dev mode:

```bash
pnpm tauri dev
```

## 2. Token Setup

In backend system config:

- `crawler_ingest_enabled = true`
- `crawler_integration_token = <strong-random-token>`
- `crawler_draft_owner_user_id = 1` (or another dedicated operator account)

In worker env (`crawler-worker/.env`):

```bash
CRAWLER_BACKEND_BASE_URL=http://127.0.0.1:9091
CRAWLER_BACKEND_CRAWLER_TOKEN=<same-token>
CRAWLER_BACKEND_DEVICE_ID=local-worker-01
CRAWLER_BACKEND_REQUEST_ORIGIN=http://localhost:3001
CRAWLER_URL_POLICY_ALLOW_PRIVATE=false
CRAWLER_URL_POLICY_ALLOWLIST=
```

`CRAWLER_URL_POLICY_ALLOWLIST` supports comma-separated `host/ip/cidr`, for example:

```bash
CRAWLER_URL_POLICY_ALLOWLIST=localhost,127.0.0.1,10.0.0.0/8
```

## 3. Operational Flow

1. Create quick task or batch task from desktop.
2. Wait for `review_pending` items.
3. Approve/reject/recrawl in candidate pool.
4. Push approved items.
5. Verify draft records in admin post list.

## 4. Rollback Controls

Emergency stop:

- Set `crawler_ingest_enabled = false` in backend config.

This blocks all crawler integration endpoints immediately.

Local rollback:

- Stop worker and desktop processes.
- Restore previous release tag for `crawler-worker` and `crawler-desktop`.

## 5. Troubleshooting

### `401/403` when pushing

- Verify crawler token header matches backend config.
- Check device id header is present.
- Ensure backend `crawler_ingest_enabled` is `true`.

### Push failed with upload error

- Confirm backend storage is enabled.
- Check image MIME and size limits.
- Validate local cache path and file readability.

### `private or loopback address is blocked`

- The worker blocks private/loopback/link-local targets by default.
- Add trusted local targets via `CRAWLER_URL_POLICY_ALLOWLIST`.
- Only for local debugging, you can set `CRAWLER_URL_POLICY_ALLOW_PRIVATE=true`.

### High local resource usage

- Lower `max_global_concurrency` to `2`.
- Keep `max_domain_concurrency` at `1`.
- Reduce batch size in site profile.

### Duplicate rejects too aggressive

- Inspect normalized URL and fingerprint generation logs.
- Adjust source URL normalization strategy for target domains.
