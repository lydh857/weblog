#!/usr/bin/env bash
set -euo pipefail

# 用法：
#   DB_ROOT_PASSWORD=xxx ./deploy/mysql/slow-query-topn.sh
#   TOP_N=20 DB_ROOT_PASSWORD=xxx ./deploy/mysql/slow-query-topn.sh

TOP_N="${TOP_N:-15}"

if [[ -z "${DB_ROOT_PASSWORD:-}" ]]; then
  echo "ERROR: DB_ROOT_PASSWORD is required"
  exit 1
fi

if docker ps --format '{{.Names}}' | grep -q '^weblog-mysql$'; then
  MYSQL_CONTAINER="weblog-mysql"
elif docker ps --format '{{.Names}}' | grep -q '^blog-mysql$'; then
  MYSQL_CONTAINER="blog-mysql"
else
  echo "ERROR: mysql container not found (weblog-mysql/blog-mysql)"
  exit 1
fi

SQL="
SELECT
  DIGEST_TEXT AS sample_sql,
  COUNT_STAR AS exec_count,
  ROUND(SUM_TIMER_WAIT/1000000000000, 2) AS total_time_s,
  ROUND(AVG_TIMER_WAIT/1000000000, 2) AS avg_time_ms,
  ROUND(MAX_TIMER_WAIT/1000000000, 2) AS max_time_ms,
  SUM_ROWS_EXAMINED AS rows_examined
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'weblog'
  AND DIGEST_TEXT IS NOT NULL
ORDER BY SUM_TIMER_WAIT DESC
LIMIT ${TOP_N};
"

echo "== MySQL top ${TOP_N} by total latency (${MYSQL_CONTAINER}) =="
docker exec -i "${MYSQL_CONTAINER}" mysql \
  -uroot "-p${DB_ROOT_PASSWORD}" \
  --default-character-set=utf8mb4 \
  -e "${SQL}"
