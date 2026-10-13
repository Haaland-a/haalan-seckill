#!/usr/bin/env bash
# =============================================================================
#  haalan 数据还原（在【新服务器】上执行）
#  ---------------------------------------------------------------------------
#  把 backup-volumes.sh 产出的备份目录，还原成新机器上这套 compose 的数据卷。
#
#  核心思路：备份里记的是「角色名」（redis-data / es-data / mq-data / nacos-data …），
#  不是旧机器的卷名。新机器上 compose 会按它自己的项目名创建卷，脚本用
#  `docker compose config` 反查出真实的卷名，再把数据灌进去并修好属主。
#  这样旧机叫 root_redis-data、新机叫 haalan_redis-data 都能正确对上。
#
#  用法：
#     bash scripts/restore-volumes.sh -i /root/haalan-backup
#     bash scripts/restore-volumes.sh -i /root/haalan-backup --volumes-only
#     bash scripts/restore-volumes.sh -i /root/haalan-backup --dry-run
#     bash scripts/restore-volumes.sh -h
#
#  典型换机流程：
#     1) 新机器装好 docker + compose，把 haalan/ haalan_/ haalan-admin/ 传上去
#     2) cd /root/haalan/deploy && vi .env   （COMPOSE_PROJECT_NAME 留空！）
#     3) 把备份目录传上来
#     4) bash scripts/restore-volumes.sh -i /root/haalan-backup
#     5) docker compose ps / 冒烟验证
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

IN_DIR=""
DRY_RUN=0
VOLUMES_ONLY=0
SKIP_MYSQL=0
SKIP_MQ=0
SKIP_SEATA=0
ASSUME_YES=0

usage() {
  cat <<'EOF'
用法: bash scripts/restore-volumes.sh -i <备份目录> [选项]

必填:
  -i, --in DIR        backup-volumes.sh 产出的备份目录

选项:
      --volumes-only  只还原数据卷，不导入 MySQL / RabbitMQ（手工控制时用）
      --skip-mysql    不导入 MySQL
      --skip-mq       不导入 RabbitMQ definitions
      --skip-seata    不还原 /root/seata 配置
      --dry-run       只打印将要做什么，不改任何东西
  -y, --yes           不交互确认
  -h, --help          显示本帮助

执行阶段:
  1. 解析新机器上 compose 的真实卷名（角色名 -> 新卷名）
  2. 建缺失的卷，把 tar 解开并修属主（必须在中间件启动【之前】做）
  3. 起中间件：mysql redis mq nacos es
  4. 导入 MySQL 逻辑备份 + RabbitMQ definitions
  5. 还原 /root/seata，起其余服务
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -i|--in)        IN_DIR="$2"; shift 2 ;;
    --volumes-only) VOLUMES_ONLY=1; shift ;;
    --skip-mysql)   SKIP_MYSQL=1; shift ;;
    --skip-mq)      SKIP_MQ=1; shift ;;
    --skip-seata)   SKIP_SEATA=1; shift ;;
    --dry-run)      DRY_RUN=1; shift ;;
    -y|--yes)       ASSUME_YES=1; shift ;;
    -h|--help)      usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[ -n "$IN_DIR" ] || { echo "缺少 -i <备份目录>" >&2; usage >&2; exit 2; }
[ -d "$IN_DIR" ] || { echo "备份目录不存在: $IN_DIR" >&2; exit 1; }
[ -f "$IN_DIR/MANIFEST.txt" ] || { echo "备份目录里没有 MANIFEST.txt，确认是不是 backup-volumes.sh 产出的" >&2; exit 1; }
[ -f "$COMPOSE_FILE" ] || { echo "找不到 $COMPOSE_FILE" >&2; exit 1; }

log()  { printf '\n==> %s\n' "$*"; }
run()  { if [ "$DRY_RUN" = "1" ]; then printf '  [dry-run] %s\n' "$*"; else "$@"; fi; }

command -v docker >/dev/null 2>&1 || { echo "找不到 docker 命令" >&2; exit 1; }

ENV_FILE="$DEPLOY_DIR/.env"
envval() { [ -f "$ENV_FILE" ] && grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }

log "还原参数"
echo "  备份目录 : $IN_DIR"
echo "  编排文件 : $COMPOSE_FILE"
echo "  干跑     : $([ "$DRY_RUN" = "1" ] && echo 是 || echo 否)"

# ------------------------------------------------------------------ 1. 解析新卷名
# 用 compose 自己算出来的名字，避免手工拼 `项目名_角色名` 出错。
log "阶段 1/5：解析新机器上的数据卷名"
# 让 compose 自己算卷名（会应用 .env 里的 COMPOSE_PROJECT_NAME 与 volumes 声明），
# 再从这个 yaml 里把 volumes 段抽出来。不依赖 jq / python，CentOS 7 上最稳。
COMPOSE_YAML="$(docker compose -f "$COMPOSE_FILE" config 2>/dev/null || true)"
PROJ="$(envval COMPOSE_PROJECT_NAME)"
[ -z "$PROJ" ] && PROJ="$(basename "$DEPLOY_DIR" | tr '[:upper:]' '[:lower:]')"
echo "  新项目名 : $PROJ"

resolve_volume() {
  local role="$1" name=""
  if [ -n "$COMPOSE_YAML" ]; then
    name="$(printf '%s\n' "$COMPOSE_YAML" | awk -v r="$role" '
      /^volumes:/            { f=1; next }
      f && /^[^[:space:]]/   { f=0 }
      f && $1 == r":"        { hit=1; next }
      hit && $1 == "name:"   { print $2; exit }
    ')"
  fi
  # 解析不到就退回最朴素的「项目名_角色名」（compose 的默认命名规则）
  [ -n "$name" ] && printf '%s' "$name" || printf '%s_%s' "$PROJ" "$role"
}

if [ -z "$(envval COMPOSE_PROJECT_NAME)" ]; then
  echo "  ✓ .env 里 COMPOSE_PROJECT_NAME 为空（换机正确姿势）"
else
  echo "  ⚠ .env 里 COMPOSE_PROJECT_NAME=$(envval COMPOSE_PROJECT_NAME)"
  echo "    换机时它应当留空；若沿用旧项目名，必须确保新机上没有同名残留资源。"
fi

# 角色 -> 新卷名 映射表，后面反复用
ROLES="redis-data es-data es-plugins mq-data mq-plugins nacos-data"
declare -A TARGET=()
printf '  %-14s %s\n' "角色" "新卷名"
for role in $ROLES; do
  TARGET[$role]="$(resolve_volume "$role")"
  printf '  %-14s %s\n' "$role" "${TARGET[$role]}"
done

if [ "$ASSUME_YES" != "1" ] && [ "$DRY_RUN" != "1" ]; then
  printf '\n将按上面的映射还原数据。继续？[y/N] '
  read -r ans
  case "$ans" in y|Y|yes|YES) ;; *) echo "已取消"; exit 0 ;; esac
fi

# ------------------------------------------------------------------ 2. 还原数据卷
log "阶段 2/5：还原数据卷（中间件启动前必须完成）"
for role in $ROLES; do
  tarf="$IN_DIR/volumes/$role.tar.gz"
  if [ ! -f "$tarf" ]; then
    echo "  - 跳过 $role（备份里没有）"
    continue
  fi
  vol="${TARGET[$role]}"
  # MANIFEST 里记的是旧机器上的属主，新机器沿用同样的 uid:gid
  owner="$(awk -v r="$role" -F'\t' '$1==r {print $3}' "$IN_DIR/MANIFEST.txt" | head -1)"
  echo "  -> $role  =>  $vol   (属主 ${owner:-未记录})"

  if ! docker volume inspect "$vol" >/dev/null 2>&1; then
    run docker volume create "$vol" >/dev/null
  fi
  dest="/var/lib/docker/volumes/$vol/_data"
  run mkdir -p "$dest"
  # 卷可能刚被 compose 建出来、里面是空的；先清干净再解包
  run find "$dest" -mindepth 1 -delete
  run tar -xzf "$tarf" -C "$dest"
  if [ -n "$owner" ]; then
    run chown -R "$owner" "$dest"
  fi
done

if [ "$VOLUMES_ONLY" = "1" ]; then
  log "只还原数据卷（--volumes-only），到此结束"
  echo "接下来手工执行： docker compose -f $COMPOSE_FILE up -d"
  exit 0
fi

# ------------------------------------------------------------------ 3. 起中间件
log "阶段 3/5：启动中间件（mysql redis mq nacos es）"
run docker compose -f "$COMPOSE_FILE" up -d mysql redis mq nacos es

if [ "$DRY_RUN" != "1" ]; then
  log "等待 mysql 健康"
  for i in $(seq 1 90); do
    st="$(docker inspect -f '{{.State.Health.Status}}' mysql8 2>/dev/null || echo unknown)"
    [ "$st" = "healthy" ] && { echo "  mysql8 healthy"; break; }
    sleep 2
  done
  [ "$(docker inspect -f '{{.State.Health.Status}}' mysql8 2>/dev/null)" = "healthy" ] \
    || echo "  ! mysql8 未在 180 秒内变健康，后续导入可能失败"

  log "等待 nacos 就绪"
  for i in $(seq 1 90); do
    if curl -sf http://localhost:8848/nacos/v1/console/health/readiness >/dev/null 2>&1; then
      echo "  nacos 就绪"; break
    fi
    sleep 2
  done
fi

# ------------------------------------------------------------------ 4. MySQL / RabbitMQ
if [ "$SKIP_MYSQL" != "1" ] && [ -f "$IN_DIR/mysql/all-databases.sql.gz" ]; then
  log "阶段 4/5：导入 MySQL（8 个库）"
  MYSQL_PW="$(envval MYSQL_ROOT_PASSWORD)"; MYSQL_PW="${MYSQL_PW:-123456}"
  echo "  注意：这会覆盖同名库里的数据"
  if [ "$DRY_RUN" = "1" ]; then
    echo "  [dry-run] gunzip -c $IN_DIR/mysql/all-databases.sql.gz | docker exec -i mysql8 mysql -uroot -p***"
  else
    gunzip -c "$IN_DIR/mysql/all-databases.sql.gz" \
      | docker exec -i -e MYSQL_PWD="$MYSQL_PW" mysql8 mysql -uroot
    echo "  导入完成"
    docker exec -e MYSQL_PWD="$MYSQL_PW" mysql8 mysql -uroot -N -e 'SHOW DATABASES' \
      | grep -E '^(user-db|item-db|order-db|seckill-db|seata|nacos|xxl_job)$' \
      | sed 's/^/    ✓ /'
  fi
else
  log "阶段 4/5：跳过 MySQL 导入"
fi

if [ "$SKIP_MQ" != "1" ] && [ -f "$IN_DIR/rabbitmq/definitions.json" ]; then
  log "导入 RabbitMQ definitions（用户 / vhost / 队列 / 交换机 / 绑定）"
  if [ "$DRY_RUN" = "1" ]; then
    echo "  [dry-run] docker cp + rabbitmqctl import_definitions"
  else
    docker cp "$IN_DIR/rabbitmq/definitions.json" mq:/tmp/haalan-definitions.json >/dev/null
    if docker exec mq rabbitmqctl import_definitions /tmp/haalan-definitions.json >/dev/null 2>&1; then
      echo "  导入完成"
      docker exec mq rabbitmqctl list_vhosts 2>/dev/null | sed 's/^/    vhost: /' || true
    else
      echo "  ! import_definitions 失败（mnesia 已随卷还原，通常不影响）"
    fi
    docker exec mq rm -f /tmp/haalan-definitions.json >/dev/null 2>&1 || true
  fi
fi

# ------------------------------------------------------------------ 5. Seata + 其余
if [ "$SKIP_SEATA" != "1" ] && [ -f "$IN_DIR/binds/seata.tar.gz" ]; then
  log "还原 Seata 服务端配置 /root/seata"
  run mkdir -p /root/seata
  if [ "$DRY_RUN" = "1" ]; then
    echo "  [dry-run] tar -xzf $IN_DIR/binds/seata.tar.gz -C /root/seata"
  else
    tar -xzf "$IN_DIR/binds/seata.tar.gz" -C /root/seata
    ls -la /root/seata | sed 's/^/    /'
  fi
fi

log "阶段 5/5：启动其余服务"
run docker compose -f "$COMPOSE_FILE" up -d

if [ "$DRY_RUN" != "1" ]; then
  log "等待服务注册到 Nacos（最多 180 秒）"
  for i in $(seq 1 60); do
    n="$(curl -sf 'http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100' 2>/dev/null \
         | sed -n 's/.*"count":\([0-9]*\).*/\1/p')"
    if [ -n "$n" ] && [ "$n" -ge 7 ]; then echo "  已注册 $n 个服务"; break; fi
    sleep 3
  done

  log "冒烟检查"
  for u in "http://localhost/|前端:80" "http://localhost:81/|管理端:81" "http://localhost:8090/|Sentinel:8090"; do
    url="${u%%|*}"; name="${u##*|}"
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" || echo 000)"
    printf '  %-16s -> %s\n' "$name" "$code"
  done
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 'http://localhost:8080/api/user/info' || echo 000)"
  printf '  %-16s -> %s  (401 表示路由与鉴权正常)\n' "gateway:8080" "$code"
fi

log "还原完成"
echo "如遇问题，检查："
echo "  docker compose -f $COMPOSE_FILE ps"
echo "  docker logs --tail 100 <容器名>"
