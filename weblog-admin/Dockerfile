FROM node:20-alpine AS builder
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@latest --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm generate

FROM nginx:alpine
COPY --from=builder /app/.output/public /usr/share/nginx/html/admin
COPY nginx-admin.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
