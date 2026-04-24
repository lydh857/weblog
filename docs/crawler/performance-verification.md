# Crawler Stack Performance Verification

Date: 2026-04-21

## Target Environments

- Server: 2 vCPU / 2 GB RAM / 60 GB SSD (ingestion-only)
- Local worker: i7-13650HX / 16 GB RAM (crawler execution)

## Verification Scope

- Backend ingestion module builds and starts with crawler additions.
- Desktop shell frontend bundles successfully.
- Worker python modules pass syntax compilation.
- Default concurrency and retry limits tuned for low-risk baseline.

## Executed Checks

1. Backend package check

```bash
mvn --% -f weblog-backend/pom.xml -pl weblog-api -am -Dmaven.test.skip=true package
```

Observed: `BUILD SUCCESS`, total time about `20.8s`.

2. Worker syntax compile check

```bash
python -m compileall crawler-worker/app
```

Observed: all modules compiled successfully.

3. Desktop build check

```bash
npm --prefix crawler-desktop install
npm --prefix crawler-desktop run build
```

Observed: build success, primary JS bundle about `449.73 kB` (gzip `140.96 kB`).

## Tuned Defaults

- `max_global_concurrency = 3`
- `max_domain_concurrency = 1`
- `max_retry_count = 3`
- `request_timeout_seconds = 20`
- `cache_max_bytes = 10 GB`

These defaults prioritize stability and low peak usage on constrained server deployments.

## Follow-up Runtime Benchmarks

- Run 100-URL batch against 3 representative domains and measure:
  - mean extraction latency
  - dynamic fallback ratio
  - image staging throughput
  - push success ratio
- Collect peak memory and CPU from local worker during crawl windows.
- Validate no server-side crawl workload beyond ingestion/persistence endpoints.
