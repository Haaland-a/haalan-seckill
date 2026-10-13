#!/usr/bin/env bash
# =============================================================================
#  Sentinel 控制台部署（在服务器上执行）
#  ---------------------------------------------------------------------------
#  用法：
#     bash deploy-sentinel.sh build     # 用 deploy-assets/sentinel/sentinel-dashboard.jar 构建镜像
#     bash deploy-sentinel.sh up        # 启动/更新容器（8090）
#     bash deploy-sentinel.sh status    # 看状态 + 探活
#     bash deploy-sentinel.sh logs      # 跟踪日志
#     bash deploy-sentinel.sh down      # 停止并删除容器（**不动镜像、不动数据**）
#     bash deploy-sentinel.sh all       # build + up + status
#
#  为什么不用 `docker compose --profile tools up -d sentinel-dashboard`：
#     compose 会先尝试 pull `haalan/sentinel-dashboard:1.8.6`，而这台服务器
#     连不上 registry-1.docker.io（实测 `no such host`），且 compose 的 pull
#     路径**不走** /etc/docker/daemon.json 里的加速器。所以本脚本直接用
#     `docker run` 起本地已构建好的镜像，完全不触发 pull。
#
#  安全边界：
#     · 只操作 sentinel-dashboard 一个容器，不碰任何中间件
#     · 不挂载任何数据卷（控制台的规则存在内存/本地文件，无状态）
#     · 只接入已存在的网络（默认 root_haalan），不创建、不删除网络
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSET_DIR="$DEPLOY_DIR/deploy-assets/sentinel"
JAR="$ASSET_DIR/sentinel-dashboard.jar"

# 与 .env 保持一致；这里给默认值，便于单独执行
IMAGE="${SENTINEL_IMAGE:-haalan/sentinel-dashboard:1.8.6}"
CONTAINER="${SENTINEL_CONTAINER:-sentinel-dashboard}"
NETWORK="${HAALAN_NETWORK:-root_haalan}"
PORT="${SENTINEL_PORT:-8090}"

die() { echo "错误: $*" >&2; exit 1; }

do_build() {
  [ -f "$JAR" ] || die "缺少 jar: $JAR
请先从本机上传：scp E:/Personal/Desktop/jaalan/sentinel-dashboard.jar root@<host>:$ASSET_DIR/"
  echo "==> 构建镜像 $IMAGE（上下文 $ASSET_DIR）"
  docker build -t "$IMAGE" "$ASSET_DIR"
  echo "==> 完成: $(docker image inspect -f '{{.Id}} {{.Size}} bytes' "$IMAGE")"
}

do_up() {
  docker image inspect "$IMAGE" >/dev/null 2>&1 || die "镜像不存在: $IMAGE（先跑 build）"
  docker network inspect "$NETWORK" >/dev/null 2>&1 || die "网络不存在: $NETWORK（用 HAALAN_NETWORK=xxx 指定）"

  if docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "==> 已存在同名容器，原地替换（--force-recreate 语义）"
    docker rm -f "$CONTAINER" >/dev/null
  fi

  echo "==> 启动 $CONTAINER（网络 $NETWORK，端口 $PORT）"
  docker run -d \
    --name "$CONTAINER" \
    --restart always \
    --network "$NETWORK" \
    -p "${PORT}:8090" \
    --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
    "$IMAGE"
}

do_status() {
  echo "==> 容器状态"
  docker ps -a --filter "name=^${CONTAINER}$" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
  echo
  echo "==> 探活 http://127.0.0.1:${PORT}/"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://127.0.0.1:${PORT}/" || echo ERR)"
  echo "  HTTP $code   （200 = 控制台已就绪；默认账号密码均为 sentinel）"
  echo
  echo "==> 确认中间件未受影响"
  for c in mysql8 redis nacos es kibana mq seata xxl-job-admin; do
    printf '  %-16s %s\n' "$c" "$(docker inspect -f '{{.State.Status}} since {{.State.StartedAt}}' "$c" 2>/dev/null || echo '不存在')"
  done
}

case "${1:-all}" in
  build)  do_build ;;
  up)     do_up ;;
  status) do_status ;;
  logs)   docker logs -f --tail 100 "$CONTAINER" ;;
  down)   echo "==> 停止并删除容器 $CONTAINER（镜像与数据不动）"; docker rm -f "$CONTAINER" ;;
  all)    do_build; do_up; sleep 8; do_status ;;
  *)      sed -n '2,20p' "$0"; exit 2 ;;
esac
