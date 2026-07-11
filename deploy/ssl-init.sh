#!/bin/bash
# 首次申请 Let's Encrypt SSL 证书
# 用法: ./ssl-init.sh yourdomain.com your@email.com
set -euo pipefail

DOMAIN="${1:?请提供域名，例如: ./ssl-init.sh yourdomain.com admin@yourdomain.com}"
EMAIL="${2:?请提供邮箱}"
NGINX_SSL_DIR="./nginx/ssl"

echo "正在为 $DOMAIN 申请SSL证书..."

# 使用 certbot standalone 模式（需要先停止 nginx）
certbot certonly \
    --standalone \
    --preferred-challenges http \
    -d "$DOMAIN" \
    -d "www.$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email

# 复制证书到 nginx 目录
mkdir -p "$NGINX_SSL_DIR"
cp "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" "$NGINX_SSL_DIR/"
cp "/etc/letsencrypt/live/$DOMAIN/privkey.pem" "$NGINX_SSL_DIR/"

echo "SSL证书申请完成，已复制到 $NGINX_SSL_DIR"
echo "请重启 nginx: docker compose -f docker-compose.prod.yml restart nginx"
