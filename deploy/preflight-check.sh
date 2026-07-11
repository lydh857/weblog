#!/usr/bin/env bash
set -euo pipefail

# 用法:
#   ./deploy/preflight-check.sh https://yourdomain.com /opt/weblog/.env.prod

BASE_URL="${1:-}"
ENV_FILE="${2:-/opt/weblog/.env.prod}"

if [ -z "$BASE_URL" ]; then
  echo "用法: $0 <BASE_URL> [ENV_FILE]"
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "缺少 curl 命令"
  exit 1
fi

pass() {
  echo "[PASS] $1"
}

fail() {
  echo "[FAIL] $1"
  exit 1
}

warn() {
  echo "[WARN] $1"
}

check_env_file() {
  if [ ! -f "$ENV_FILE" ]; then
    fail "环境变量文件不存在: $ENV_FILE"
  fi

  local bad_count
  bad_count=$(grep -E "CHANGE_TO_|change-this-in-production|local-dev" "$ENV_FILE" | wc -l | tr -d ' ')
  if [ "$bad_count" -gt 0 ]; then
    fail "环境变量文件仍包含默认/弱密钥占位符"
  fi

  pass "环境变量文件检查通过"
}

check_health() {
  local code
  code=$(curl -k -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" || true)
  if [ "$code" != "200" ]; then
    fail "健康检查失败: $BASE_URL/actuator/health -> HTTP $code"
  fi
  pass "健康检查通过"
}

check_security_headers() {
  local headers
  headers=$(curl -k -s -D - -o /dev/null "$BASE_URL" || true)

  echo "$headers" | grep -iq "strict-transport-security" || fail "缺少 HSTS 头"
  echo "$headers" | grep -iq "content-security-policy" || fail "缺少 CSP 头"
  echo "$headers" | grep -iq "x-content-type-options" || fail "缺少 X-Content-Type-Options 头"

  pass "关键安全响应头检查通过"
}

check_compose_services() {
  if ! command -v docker >/dev/null 2>&1; then
    warn "未检测到 docker，跳过容器状态检查"
    return
  fi

  local unhealthy
  unhealthy=$(docker ps --format '{{.Names}} {{.Status}}' | grep -E "unhealthy|Exited" || true)
  if [ -n "$unhealthy" ]; then
    echo "$unhealthy"
    fail "检测到异常容器状态"
  fi

  pass "容器状态检查通过"
}

echo "开始上线前核验: BASE_URL=$BASE_URL ENV_FILE=$ENV_FILE"
check_env_file
check_health
check_security_headers
check_compose_services
echo "上线前核验完成"
