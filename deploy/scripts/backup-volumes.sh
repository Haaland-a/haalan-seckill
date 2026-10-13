#!/usr/bin/env bash
# =============================================================================
#  haalan 数据备份（在【旧服务器】上执行）
#  ---------------------------------------------------------------------------
#  换服务器时，代码可以重新编译，但下面这些「有状态数据」必须搬过去：
#    · MySQL        8 个库              -> mysqldump 逻辑备份（--single-transaction 热备一致）
#    · Redis        db0 共 25 个 key    -> BGSAVE 落盘后拷贝 dump.rdb
#    · RabbitMQ     用户/vhost/队列/交换机/绑定 -> definitions.json
#                   未消费消息         -> 完整 mnesia 目录
#    · Nacos        14 条配置 + raft 状态 -> data 目录
#    · Elasticsearch products 等索引    -> _flush 后拷贝 data 目录
#    · Seata        服务端配置（/root/seata）
#    · 编排文件 / .env / 镜像与容器清单（对照用）
#
#  ⚠️ 本脚本默认【不停止任何容器】。MySQL/Redis/RabbitMQ/ES 各自用热备手段保证
#     逻辑一致，但做不到「同一瞬间」的快照。要更严格就加 --app-quiesce，
#     它会临时停掉 8 个应用容器（备份完自动拉起），中间件依旧不停。
#
#  用法：
#     bash scripts/backup-volumes.sh                    # 输出到 /root/haalan-backup
#     bash scripts/backup-volumes.sh -o /data/bk        # 指定输出目录
#     bash scripts/backup-volumes.sh --app-quiesce      # 先停应用容器（更一致）
#     bash scripts/backup-volumes.sh --no-es            # 跳过 ES（可从 MySQL 重建）
#     bash scripts/backup-volumes.sh --images           # 顺便 docker save 应用镜像
#     bash scripts/backup-volumes.sh -h
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OUT_DIR="/root/haalan-backup"
APP_QUIESCE=0
DO_ES=1
DO_IMAGES=0

usage() {
  cat <<'EOF'
用法: bash scripts/backup-volumes.sh [选项]

选项:
  -o, --out DIR      输出目录（默认 /root/haalan-backup）
      --app-quiesce  备份前先停 8 个应用容器，备份后自动拉起（中间件不停）
      --no-es        跳过 Elasticsearch（索引可从 MySQL 重建）
      --images       额外 docker save 应用镜像（+3~4 GB）
  -h, --help         显示本帮助

输出结构:
  <OUT>/MANIFEST.txt                角色 -> 卷名 -> 属主 映射表（还原时要用）
  <OUT>/mysql/all-databases.sql.gz  8 个库的逻辑备份
  <OUT>/volumes/<role>.tar.gz       redis-data / es-data / es-plugins /
                                    mq-data / mq-plugins / nacos-data
  <OUT>/rabbitmq/definitions.json   RabbitMQ 定义导出
  <OUT>/binds/seata.tar.gz          /root/seata 配置
  <OUT>/compose/                    编排文件、.env、镜像/容器/卷/网络清单
  <OUT>/images/app-images.tar       仅 --images
  <OUT>/SHA256SUMS                  校验和
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -o|--out)      OUT_DIR="$2"; shift 2 ;;
    --app-quiesce) APP_QUIESCE=1; shift ;;
    --no-es)       DO_ES=0; shift ;;
    --images)      DO_IMAGES=1; shift ;;
    -h|--help)     usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 2 ;;
  esac
done

log() { printf '\n==> %s\n' "$*"; }

command -v docker >/dev/null 2>&1 || { echo "找不到 docker 命令" >&2; exit 1; }

ENV_FILE="$DEPLOY_DIR/.env"
envval() { [ -f "$ENV_FILE" ] && grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }

PROJECT="$(envval COMPOSE_PROJECT_NAME)"; PROJECT="${PROJECT:-root}"
MYSQL_PW="$(envval MYSQL_ROOT_PASSWORD)"; MYSQL_PW="${MYSQL_PW:-123456}"
REDIS_PW="$(envval REDIS_PASSWORD)"; REDIS_PW="${REDIS_PW:-123456}"

# 卷的「角色名」-> 容器名。角色名与 compose 项目名无关，换机后按角色重新映射。
VOL_ROLES="redis-data es-data es-plugins mq-data mq-plugins nacos-data"
role_container() {
  case "$1" in
    redis-data)          echo redis ;;
    es-data|es-plugins)  echo es ;;
    mq-data|mq-plugins)  echo mq ;;
    nacos-data)          echo nacos ;;
    *)                   echo "" ;;
  esac
}

APP_CONTAINERS="gateway user-service item-service order-service search-service seckill-service frontend haalan-admin"
QUIESCED=0
restore_apps() {
  if [ "$QUIESCED" = "1" ]; then
    printf '\n==> 重新拉起应用容器\n'
    # shellcheck disable=SC2086
    docker start $APP_CONTAINERS >/dev/null 2>&1 || true
    QUIESCED=0
  fi
}
trap restore_apps EXIT

log "备份参数"
echo "  输出目录 : $OUT_DIR"
echo "  源项目名 : $PROJECT（决定读哪几个数据卷）"
echo "  停应用   : $([ "$APP_QUIESCE" = "1" ] && echo 是 || echo 否)"
echo "  含 ES    : $([ "$DO_ES" = "1" ] && echo 是 || echo 否)"

mkdir -p "$OUT_DIR"
rm -rf "$OUT_DIR/mysql" "$OUT_DIR/volumes" "$OUT_DIR/rabbitmq" "$OUT_DIR/binds" "$OUT_DIR/compose" "$OUT_DIR/images"

if [ "$APP_QUIESCE" = "1" ]; then
  log "停止应用容器（备份后会自动拉起）"
  # shellcheck disable=SC2086
  docker stop $APP_CONTAINERS >/dev/null 2>&1 || true
  QUIESCED=1
fi

# ---------------------------------------------------------------- 清单表头
MANIFEST="$OUT_DIR/MANIFEST.txt"
{
  echo "# haalan 数据备份清单"
  echo "# 生成时间: $(date -Iseconds)"
  echo "# 源主机名: $(hostname)"
  echo "# 源 compose 项目: $PROJECT"
  echo "# 格式: 角色<TAB>源卷名<TAB>属主<TAB>大小"
} > "$MANIFEST"

# ---------------------------------------------------------------- MySQL
log "备份 MySQL（热备一致性：--single-transaction）"
mkdir -p "$OUT_DIR/mysql"
DB_LIST="mysql nacos xxl_job seata user-db item-db order-db seckill-db"
# shellcheck disable=SC2086
docker exec -e MYSQL_PWD="$MYSQL_PW" mysql8 \
  mysqldump -uroot --single-transaction --routines --events --triggers \
            --hex-blob --set-gtid-purged=OFF --databases $DB_LIST \
  | gzip -9 > "$OUT_DIR/mysql/all-databases.sql.gz"
echo "  -> mysql/all-databases.sql.gz  ($(du -h "$OUT_DIR/mysql/all-databases.sql.gz" | cut -f1))"
echo "  注: /data/mysql8 这个绑定目录【不做 tar】，一律用上面的逻辑备份还原（更安全、可跨小版本）"

# ---------------------------------------------------------------- Redis 先落盘
log "让 Redis 落盘（BGSAVE）"
if docker exec redis redis-cli --no-auth-warning -a "$REDIS_PW" BGSAVE >/dev/null 2>&1; then
  for i in $(seq 1 60); do
    info="$(docker exec redis redis-cli --no-auth-warning -a "$REDIS_PW" INFO persistence 2>/dev/null || true)"
    if echo "$info" | grep -q 'rdb_bgsave_in_progress:0'; then
      if echo "$info" | grep -q 'rdb_last_bgsave_status:ok'; then
        echo "  RDB 落盘完成"
      else
        echo "  ! RDB 落盘状态不是 ok，请检查"
      fi
      break
    fi
    sleep 1
  done
else
  echo "  ! BGSAVE 调用失败（redis 可能没起），仍会拷贝现有 dump.rdb"
fi

# ---------------------------------------------------------------- ES 先 flush
if [ "$DO_ES" = "1" ]; then
  log "让 Elasticsearch flush（保证段文件落盘）"
  if curl -sf -XPOST 'http://localhost:9200/_flush?wait_if_ongoing=true' >/dev/null; then
    echo "  _flush 完成"
  else
    echo "  ! _flush 失败（ES 可能没起），仍会拷贝现有 data 目录"
  fi
fi

# ---------------------------------------------------------------- 数据卷
log "备份数据卷"
mkdir -p "$OUT_DIR/volumes"
for role in $VOL_ROLES; do
  if [ "$role" = "es-data" ] && [ "$DO_ES" = "0" ]; then
    echo "  - 跳过 $role (--no-es)"
    continue
  fi
  src="${PROJECT}_${role}"
  p="/var/lib/docker/volumes/$src/_data"
  if [ ! -d "$p" ]; then
    echo "  ! 卷不存在，跳过: $src"
    continue
  fi
  owner="$(stat -c '%u:%g' "$p")"
  size="$(du -sh "$p" 2>/dev/null | cut -f1)"
  printf '  %-14s %-24s %-8s %s\n' "$role" "$src" "$owner" "$size"
  printf '%s\t%s\t%s\t%s\n' "$role" "$src" "$owner" "$size" >> "$MANIFEST"
  tar -czf "$OUT_DIR/volumes/$role.tar.gz" -C "$p" .
done

# ---------------------------------------------------------------- 绑定挂载
log "备份绑定挂载 /root/seata"
mkdir -p "$OUT_DIR/binds"
if [ -d /root/seata ]; then
  tar -czf "$OUT_DIR/binds/seata.tar.gz" -C /root/seata .
  printf 'BIND\t%s\t%s\t%s\n' "/root/seata" "$(stat -c '%u:%g' /root/seata)" "$(du -sh /root/seata | cut -f1)" >> "$MANIFEST"
  echo "  -> binds/seata.tar.gz"
else
  echo "  ! /root/seata 不存在，跳过"
fi

# ---------------------------------------------------------------- RabbitMQ 定义
log "导出 RabbitMQ definitions"
mkdir -p "$OUT_DIR/rabbitmq"
if docker exec mq rabbitmqctl export_definitions /tmp/haalan-definitions.json >/dev/null 2>&1; then
  docker cp mq:/tmp/haalan-definitions.json "$OUT_DIR/rabbitmq/definitions.json" >/dev/null
  docker exec mq rm -f /tmp/haalan-definitions.json >/dev/null 2>&1 || true
  echo "  -> rabbitmq/definitions.json ($(du -h "$OUT_DIR/rabbitmq/definitions.json" | cut -f1))"
else
  echo "  ! export_definitions 失败（mnesia 目录仍会完整备份）"
fi

# ---------------------------------------------------------------- 编排与清单
log "记录编排文件与清单"
mkdir -p "$OUT_DIR/compose"
for f in docker-compose.yml docker-compose.apps.yml; do
  [ -f "$DEPLOY_DIR/$f" ] && cp -f "$DEPLOY_DIR/$f" "$OUT_DIR/compose/"
done
[ -f "$ENV_FILE" ] && cp -f "$ENV_FILE" "$OUT_DIR/compose/.env"
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}'          > "$OUT_DIR/compose/images.txt"     2>/dev/null || true
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}'          > "$OUT_DIR/compose/containers.txt" 2>/dev/null || true
docker volume ls                                                      > "$OUT_DIR/compose/volumes.txt"    2>/dev/null || true
docker network ls                                                     > "$OUT_DIR/compose/networks.txt"   2>/dev/null || true

# ---------------------------------------------------------------- 应用镜像
if [ "$DO_IMAGES" = "1" ]; then
  log "docker save 应用镜像"
  mkdir -p "$OUT_DIR/images"
  IMGS="$(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^(root-|haalan/)' | grep -v '<none>' || true)"
  if [ -n "$IMGS" ]; then
    # shellcheck disable=SC2086
    docker save -o "$OUT_DIR/images/app-images.tar" $IMGS
    echo "  -> images/app-images.tar ($(du -h "$OUT_DIR/images/app-images.tar" | cut -f1))"
  else
    echo "  ! 没有匹配的应用镜像（root-* / haalan/*）"
  fi
fi

# ---------------------------------------------------------------- 校验和
log "生成 SHA256SUMS"
( cd "$OUT_DIR" && find . -type f ! -name SHA256SUMS -exec sha256sum {} + > SHA256SUMS )

log "备份完成"
du -sh "$OUT_DIR"
echo
echo "下一步：把整个目录拷到新服务器"
echo "  rsync -aP $OUT_DIR/ root@<新机IP>:$OUT_DIR/"
echo "然后在【新服务器】上执行："
echo "  bash scripts/restore-volumes.sh -i $OUT_DIR"
