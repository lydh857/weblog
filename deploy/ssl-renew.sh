#!/bin/bash
# SSL 证书自动续期脚本（Let's Encrypt）
# 添加到 crontab: 0 3 * * 1 /opt/weblog/deploy/ssl-renew.sh
set -euo pipefail

DEPLOY_DIR="/opt/weblog"
DOMAIN="${1:-yourdomain.com}"

echo "[$(date)] 开始SSL证书续期..."

# 使用 certbot 续期
certbot renew --quiet --deploy-hook "docker compose -f $DEPLOY_DIR/docker-compose.prod.yml exec nginx nginx -s reload"

echo "[$(date)] SSL证书续期完成"
