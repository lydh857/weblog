#!/usr/bin/env bash
set -euo pipefail

generate_secret() {
  openssl rand -base64 48 | tr -d '\n' | tr '/+' 'ab' | cut -c1-64
}

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl 未安装，无法生成随机密钥" >&2
  exit 1
fi

echo "# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "DB_PASSWORD=$(generate_secret)"
echo "REDIS_PASSWORD=$(generate_secret)"
echo "JWT_SECRET=$(generate_secret)"
echo "CAPTCHA_SECRET_KEY=$(generate_secret)"
echo "GITHUB_CLIENT_SECRET=$(generate_secret)"
