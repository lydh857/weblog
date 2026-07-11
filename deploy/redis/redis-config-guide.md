# Redis 配置优化指南

## 📋 配置说明

### 1. 最大内存限制（必须配置）

**文件**: `redis.conf`

```conf
# 设置最大内存为 128MB（2G 服务器推荐值）
maxmemory 128mb

# 内存淘汰策略：volatile-lru（优先淘汰有过期时间的键）
maxmemory-policy volatile-lru

# 当内存达到上限时，每 100ms 检查一次
maxmemory-samples 5
```

### 2. 持久化配置（可选）

```conf
# RDB 快照（按需开启）
save 900 1
save 300 10
save 60 10000

# AOF 日志（生产环境建议开启）
appendonly yes
appendfsync everysec
```

### 3. 网络配置

```conf
# 绑定地址（生产环境不要绑定 0.0.0.0）
bind 127.0.0.1

# 端口
port 6379

# 最大连接数
maxclients 1000

# 超时时间（0 表示不超时）
timeout 300
```

### 4. 安全配置

```conf
# 设置密码（生产环境必须）
requirepass your-strong-password-here

# 禁用危险命令
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command DEBUG ""
```

## 🚀 Docker 部署配置

### docker-compose.yml

```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    container_name: weblog-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
      - ./redis.conf:/usr/local/etc/redis/redis.conf
    command: redis-server /usr/local/etc/redis/redis.conf
    # 内存限制（Docker 层面）
    deploy:
      resources:
        limits:
          memory: 256M

volumes:
  redis-data:
```

### 环境变量方式

```bash
docker run -d \
  --name weblog-redis \
  -p 6379:6379 \
  -v redis-data:/data \
  -e REDIS_MAXMEMORY=128mb \
  redis:7-alpine \
  redis-server --appendonly yes --maxmemory 128mb --maxmemory-policy volatile-lru
```

## 📊 监控命令

### 查看内存使用

```bash
redis-cli info memory
```

### 查看统计信息

```bash
redis-cli info stats
```

### 查看慢查询

```bash
redis-cli slowlog get 10
```

### 查看连接数

```bash
redis-cli info clients
```

## ⚠️ 注意事项

1. **内存限制**: 必须设置，避免 OOM 导致服务器崩溃
2. **淘汰策略**: 推荐使用 `volatile-lru` 或 `allkeys-lru`
3. **密码保护**: 生产环境必须设置强密码
4. **网络隔离**: 只允许内网访问，不要暴露到公网
5. **持久化**: 根据业务需求选择 RDB 或 AOF

## 🔗 相关文档

- [Redis 官方文档](https://redis.io/documentation)
- [Redis 内存优化](https://redis.io/docs/management/optimization/memory-optimization/)
- [Redis 配置参考](https://redis.io/docs/management/config/)
