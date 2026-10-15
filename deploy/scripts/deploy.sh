#!/usr/bin/env bash
# =============================================================================
#  haalan 应用层部署（在服务器上执行）
#  ---------------------------------------------------------------------------
#  只操作 7 个微服务 + 2 个前端，绝不碰 mysql8 / redis / nacos / es / kibana /
#  mq / seata / xxl-job-admin 这 8 个中间件容器，也不碰任何数据卷。
#
#  用法：
#     bash deploy.sh                       # 编译 jar → 重建全部 9 个应用
#     bash deploy.sh gateway               # 只重建 gateway
#     bash deploy.sh gateway user-service  # 重建指定的几个
#     bash deploy.sh frontend admin        # 只重建前端
#     bash deploy.sh --no-build            # 跳过 mvn，只用现有 jar 重建镜像
#     bash deploy.sh --list                # 列出可用的服务名
#
#  环境变量：
#     NO_BACKUP=1   跳过「给现有镜像打时间戳标签」这一步
#
#  实测数据（192.168.100.133，2 核 / 7.8GB）：
#     mvn -o clean package -DskipTests   ≈ 26 秒（全量 clean）
#     mvn -o package -DskipTests         ≈ 15 秒（增量）
#     docker compose build（单服务）      ≈ 5-20 秒
# =============================================================================
set -euo pipefail

# 脚本放在 deploy/scripts/ 下，所以要退两层才是仓库根。
# 之前这里少退了一层，导致 REPO_ROOT 算成 deploy/，mvn 找不到 pom.xml。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.apps.yml"

ALL_SERVICES=(gateway user-service item-service order-service search-service seckill-service cart-service frontend admin)

# 服务器上 JDK/Maven 只写在 /etc/profile 里，ssh 非登录 shell 读不到，必须显式设置
export JAVA_HOME="${JAVA_HOME:-/usr/local/jdk17}"
export PATH="$JAVA_HOME/bin:/usr/local/maven/bin:$PATH"

DO_BUILD=1
SERVICES=()

for arg in "$@"; do
  case "$arg" in
    --no-build) DO_BUILD=0 ;;
    --list)     printf '%s\n' "${ALL_SERVICES[@]}"; exit 0 ;;
    -h|--help)  sed -n '2,23p' "$0"; exit 0 ;;
    -*)         echo "未知参数: $arg" >&2; exit 2 ;;
    *)          SERVICES+=("$arg") ;;
  esac
done

[ ${#SERVICES[@]} -eq 0 ] && SERVICES=("${ALL_SERVICES[@]}")

# 校验服务名，避免手滑传错
for s in "${SERVICES[@]}"; do
  found=0
  for a in "${ALL_SERVICES[@]}"; do [ "$s" = "$a" ] && found=1; done
  if [ "$found" = "0" ]; then
    echo "未知服务: $s" >&2
    echo "可用: ${ALL_SERVICES[*]}" >&2
    exit 2
  fi
done

cd "$DEPLOY_DIR"

# ---------------------------------------------------------------------------
# 1. 编译后端 jar（纯前端部署可跳过）
# ---------------------------------------------------------------------------
needs_backend=0
for s in "${SERVICES[@]}"; do
  [ "$s" != "frontend" ] && [ "$s" != "admin" ] && needs_backend=1
done

if [ "$DO_BUILD" = "1" ] && [ "$needs_backend" = "1" ]; then
  echo "==> 编译后端（mvn -o clean package -DskipTests）"
  ( cd "$REPO_ROOT" && mvn -o clean package -DskipTests ) || {
    echo "编译失败，已中止。中间件与现有容器未受影响。" >&2
    exit 1
  }
  echo
  echo "==> jar 产物"
  for svc in haalan-gateway user-service item-service order-service search-service seckill-service cart-service; do
    jar="$REPO_ROOT/$svc/target/$svc-0.0.1-SNAPSHOT.jar"
    [ -f "$jar" ] && printf '  %-18s %s\n' "$svc" "$(du -h "$jar" | cut -f1)" \
                  || { printf '  %-18s [缺失]\n' "$svc"; exit 1; }
  done
fi

# ---------------------------------------------------------------------------
# 2. 记录部署前的中间件状态，便于事后核对
# ---------------------------------------------------------------------------
MW=(mysql8 redis nacos es kibana mq seata xxl-job-admin)
echo
echo "==> 部署前中间件状态（部署后应完全一致）"
for c in "${MW[@]}"; do
  printf '  %-16s %s\n' "$c" "$(docker inspect -f '{{.State.Status}} since {{.State.StartedAt}}' "$c" 2>/dev/null || echo '不存在')"
done

# ---------------------------------------------------------------------------
# 3. 给现有镜像打时间戳备份标签，方便回滚
#    （只新增 tag，不删除、不覆盖 :latest 之外的任何东西）
# ---------------------------------------------------------------------------
if [ "${NO_BACKUP:-0}" != "1" ]; then
  STAMP="$(date +%Y%m%d-%H%M%S)"
  echo
  echo "==> 备份现有镜像标签（回滚用）"
  for s in "${SERVICES[@]}"; do
    case "$s" in
      frontend) img=root-frontend ;;
      admin)    img=root-admin ;;
      *)        img="root-$s" ;;
    esac
    if docker image inspect "$img:latest" >/dev/null 2>&1; then
      docker tag "$img:latest" "$img:$STAMP"
      printf '  %-22s -> %s\n' "$img:latest" "$img:$STAMP"
    else
      printf '  %-22s [无现有镜像，跳过]\n' "$img:latest"
    fi
  done
  echo "  回滚命令示例: docker tag root-gateway:$STAMP root-gateway:latest && \\"
  echo "                docker compose -f $COMPOSE_FILE up -d --force-recreate gateway"
  echo "  （全部备份标签: docker images 'root-*'）"
fi

# ---------------------------------------------------------------------------
# 4. 构建镜像并就地替换应用容器
#    注意：这里**不加** --remove-orphans。中间件属于同一个 compose 项目
#    （root），会被列成 orphan，加了该参数就会把它们删掉。
# ---------------------------------------------------------------------------
echo
echo "==> 校验编排语法"
docker compose -f "$COMPOSE_FILE" config --quiet && echo "  语法 OK"

echo
echo "==> 构建并替换: ${SERVICES[*]}"
docker compose -f "$COMPOSE_FILE" build "${SERVICES[@]}"
docker compose -f "$COMPOSE_FILE" up -d "${SERVICES[@]}"

# ---------------------------------------------------------------------------
# 5. 状态与冒烟检查
# ---------------------------------------------------------------------------
echo
echo "==> 应用容器状态"
docker compose -f "$COMPOSE_FILE" ps

echo
echo "==> 部署后中间件状态（应与部署前一致）"
for c in "${MW[@]}"; do
  printf '  %-16s %s\n' "$c" "$(docker inspect -f '{{.State.Status}} since {{.State.StartedAt}}' "$c" 2>/dev/null || echo '不存在')"
done

echo
echo "==> 等待服务注册到 Nacos（最多 150 秒）"
for i in $(seq 1 50); do
  n=$(curl -s --max-time 5 "http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100" 2>/dev/null \
      | grep -o '"count":[0-9]*' | cut -d: -f2 || echo 0)
  if [ "${n:-0}" -ge 8 ]; then echo "  已注册 ${n} 个服务"; break; fi
  sleep 3
done

echo
echo "==> 冒烟检查"
printf '  frontend  :80   -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1/ || echo ERR)"
printf '  admin     :81   -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1:81/ || echo ERR)"
printf '  gateway   :8080 -> %s  (401 表示路由与鉴权都正常)\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1:8080/api/user/info || echo ERR)"
echo
echo "==> Nacos 服务列表"
curl -s --max-time 8 "http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100" 2>/dev/null || echo "  (查询失败)"
echo
echo "完成。"
