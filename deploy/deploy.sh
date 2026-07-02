#!/bin/bash
set -euo pipefail

# ===== 配置 =====
APP_NAME="weblog"
DEPLOY_DIR="/opt/weblog"
BACKUP_DIR="/opt/backup"
LOG_FILE="/var/log/weblog/deploy.log"
HEALTH_URL="http://localhost:9091/actuator/health"
HEALTH_TIMEOUT=60
COMPOSE_FILE="docker-compose.prod.yml"
MAIN_REMOTE="github"
MAIN_BRANCH="master"

# ===== 颜色输出 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"; }
warn() { echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARN:${NC} $1" | tee -a "$LOG_FILE"; }
error() { echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$LOG_FILE"; }

# ===== Step 1: 备份 =====
backup() {
    log "开始备份..."
    mkdir -p "$BACKUP_DIR"
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local backup_file="$BACKUP_DIR/${APP_NAME}_${timestamp}.tar.gz"

    if [ -d "$DEPLOY_DIR" ]; then
        tar -czf "$backup_file" \
            -C "$DEPLOY_DIR" \
            docker-compose.prod.yml .env.prod nginx/ \
            --ignore-failed-read 2>/dev/null || true
        log "备份完成: $backup_file"
    else
        warn "部署目录不存在，跳过备份"
    fi

    # 保留最近5个备份
    ls -t "$BACKUP_DIR"/${APP_NAME}_*.tar.gz 2>/dev/null | tail -n +6 | xargs -r rm -f
    log "清理旧备份完成"
}

# ===== Step 2: 拉取最新代码 =====
pull_code() {
    log "拉取最新代码..."
    cd "$DEPLOY_DIR"

    if ! git remote get-url "$MAIN_REMOTE" >/dev/null 2>&1; then
        error "未找到主线远程: $MAIN_REMOTE"
        return 1
    fi

    git fetch "$MAIN_REMOTE" "$MAIN_BRANCH"
    git pull --ff-only "$MAIN_REMOTE" "$MAIN_BRANCH"
    log "代码更新完成"
}

wait_mysql_healthy() {
    log "等待 MySQL 健康检查通过..."
    local elapsed=0
    local interval=5
    local timeout=120

    while [ $elapsed -lt $timeout ]; do
        local status
        status=$(docker inspect -f '{{.State.Health.Status}}' weblog-mysql 2>/dev/null || echo "starting")
        if [ "$status" = "healthy" ]; then
            log "MySQL 已就绪 (${elapsed}s)"
            return 0
        fi

        sleep $interval
        elapsed=$((elapsed + interval))
    done

    error "MySQL 健康检查超时"
    return 1
}

run_migrations() {
    log "执行数据库迁移脚本..."
    cd "$DEPLOY_DIR"

    local migration_dir="deploy/mysql"
    if [ ! -d "$migration_dir" ]; then
        warn "迁移目录不存在，跳过迁移"
        return 0
    fi

    shopt -s nullglob
    local sql_files=("$migration_dir"/*.sql)
    shopt -u nullglob

    if [ ${#sql_files[@]} -eq 0 ]; then
        log "未发现迁移脚本，跳过迁移"
        return 0
    fi

    for sql_file in "${sql_files[@]}"; do
        log "执行迁移: $sql_file"
        docker compose -f "$COMPOSE_FILE" exec -T mysql \
            sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot weblog' < "$sql_file"
    done

    log "数据库迁移完成"
}

# ===== Step 3: 构建并部署 =====
deploy() {
    log "开始构建和部署..."
    cd "$DEPLOY_DIR"

    # 构建镜像
    docker compose -f "$COMPOSE_FILE" build --no-cache

    # 停止旧容器
    docker compose -f "$COMPOSE_FILE" down

    # 先启动基础设施并执行迁移
    docker compose -f "$COMPOSE_FILE" up -d mysql redis
    wait_mysql_healthy
    run_migrations

    # 再启动应用服务
    docker compose -f "$COMPOSE_FILE" up -d weblog-api weblog-user nginx

    log "容器启动完成"
}

# ===== Step 4: 健康检查 =====
health_check() {
    log "等待服务启动，开始健康检查..."
    local elapsed=0
    local interval=5

    while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
        sleep $interval
        elapsed=$((elapsed + interval))

        local status=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
        if [ "$status" = "200" ]; then
            log "健康检查通过 (${elapsed}s)"
            return 0
        fi
        log "等待中... (${elapsed}s/${HEALTH_TIMEOUT}s, HTTP: $status)"
    done

    error "健康检查失败！超时 ${HEALTH_TIMEOUT}s"
    return 1
}

# ===== Step 5: 回滚 =====
rollback() {
    error "部署失败，开始回滚..."
    cd "$DEPLOY_DIR"

    local latest_backup=$(ls -t "$BACKUP_DIR"/${APP_NAME}_*.tar.gz 2>/dev/null | head -1)
    if [ -n "$latest_backup" ]; then
        tar -xzf "$latest_backup" -C "$DEPLOY_DIR"
        docker compose -f "$COMPOSE_FILE" down
        docker compose -f "$COMPOSE_FILE" up -d
        log "回滚完成: $latest_backup"
    else
        error "无可用备份，回滚失败！"
        exit 1
    fi
}

# ===== 主流程 =====
main() {
    log "========== 开始部署 ${APP_NAME} =========="

    backup
    pull_code

    if deploy && health_check; then
        log "========== 部署成功 =========="
        # 清理无用镜像
        docker image prune -f 2>/dev/null || true
    else
        rollback
        error "========== 部署失败，已回滚 =========="
        exit 1
    fi
}

# 执行
main "$@"
