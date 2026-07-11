FROM node:20-alpine AS builder
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm generate

FROM nginx:alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /usr/share/nginx/html/admin && \
    chown -R appuser:appgroup /var/cache/nginx && \
    chown -R appuser:appgroup /var/log/nginx && \
    chown appuser:appgroup /var/run/nginx.pid 2>/dev/null; exit 0

COPY --from=builder /app/.output/public /usr/share/nginx/html/admin
COPY nginx-admin.conf /etc/nginx/conf.d/default.conf

USER appuser
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://localhost:80/admin/ || exit 1
