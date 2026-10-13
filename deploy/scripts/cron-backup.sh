#!/usr/bin/env bash
# =============================================================================
#  haalan 定时备份（crontab 入口 + 轮转）
#  ---------------------------------------------------------------------------
#  这个脚本是给 crontab 调的，也可以手工执行一次试跑。
#
#  它做三件事：
#    1. 按日期建目录，调用 backup-volumes.sh 做一份完整备份
#       （/root/haalan-backup/2026-09-03/）
#    2. 把「最新一份」软链到 /root/haalan-backup/latest
#    3. 删掉超过 --keep 天的旧备份目录
#
#  用法:
#     bash scripts/cron-backup.sh                 # 备份 + 轮转（默认保留 14 天）
#     bash scripts/cron-backup.sh --keep 7        # 只保留 7 天
#     bash scripts/cron-backup.sh --out /data/bk  # 换备份根目录
#     bash scripts/cron-backup.sh --dry-run       # 只打印将要做什么
#     bash scripts/cron-backup.sh --install       # 装进 crontab（每周日 03:30）
#     bash scripts/cron-backup.sh --uninstall     # 从 crontab 移除
#     bash scripts/cron-backup.sh --status        # 看当前 crontab 与备份目录
#     bash scripts/cron-backup.sh -h
#
#  ⚠️ 备份期间不停止任何容器。MySQL 用 --single-transaction 热备，
#     Redis 先 BGSAVE，ES 先 _flush，RabbitMQ 导出 definitions。
#     要更严格的一致性可给底层加 --app-quiesce（会短暂停应用容器）。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OUT_ROOT="/root/haalan-backup"
KEEP_DAYS=14
DRY_RUN=0
ACTION="backup"
CRON_SCHEDULE="30 3 * * 0"   # 每周日 03:30
CRON_TAG="# haalan-backup (deploy/scripts/cron-backup.sh)"

usage() {
  sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'
}

log() { printf '\n==> %s\n' "$*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --out)       OUT_ROOT="$2"; shift 2 ;;
    --keep)      KEEP_DAYS="$2"; shift 2 ;;
    --dry-run)   DRY_RUN=1; shift ;;
    --install)   ACTION="install"; shift ;;
    --uninstall) ACTION="uninstall"; shift ;;
    --status)    ACTION="status"; shift ;;
    -h|--help)   usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# ------------------------------------------------------------------ crontab 操作
cron_line() {
  printf '%s cd %s && bash scripts/cron-backup.sh --out %s --keep %s >> %s/cron.log 2>&1 %s\n' \
    "$CRON_SCHEDULE" "$DEPLOY_DIR" "$OUT_ROOT" "$KEEP_DAYS" "$OUT_ROOT" "$CRON_TAG"
}

do_install() {
  mkdir -p "$OUT_ROOT"
  local existing
  existing="$(crontab -l 2>/dev/null || true)"
  # 先去掉旧的同标签行，避免重复添加
  local cleaned
  cleaned="$(printf '%s\n' "$existing" | grep -vF "$CRON_TAG" || true)"
  printf '%s\n%s\n' "$cleaned" "$(cron_line)" | sed '/^$/d' | crontab -
  log "已写入 crontab"
  crontab -l | grep -A0 -F "$CRON_TAG"
  echo
  echo "时间表 : $CRON_SCHEDULE  (每周日 03:30)"
  echo "保留   : $KEEP_DAYS 天"
  echo "输出   : $OUT_ROOT/<日期>/"
  echo "日志   : $OUT_ROOT/cron.log"
  echo
  echo "注意: crond 在 CentOS 7 上默认不加载 /etc/profile，"
  echo "      本脚本内部已显式设置 JAVA_HOME/PATH 兜底，不依赖登录环境。"
}

do_uninstall() {
  local existing cleaned
  existing="$(crontab -l 2>/dev/null || true)"
  cleaned="$(printf '%s\n' "$existing" | grep -vF "$CRON_TAG" || true)"
  if [ "$cleaned" = "$existing" ]; then
    echo "crontab 里没有本脚本的条目，无需移除。"
    return 0
  fi
  printf '%s\n' "$cleaned" | sed '/^$/d' | crontab -
  log "已从 crontab 移除"
  crontab -l 2>/dev/null || echo "(crontab 现在是空的)"
}

do_status() {
  log "当前 crontab"
  crontab -l 2>/dev/null || echo "(空)"
  log "crond 服务状态"
  systemctl is-active crond 2>&1 || true
  log "备份目录 $OUT_ROOT"
  if [ -d "$OUT_ROOT" ]; then
    du -sh "$OUT_ROOT" 2>/dev/null || true
    ls -la "$OUT_ROOT" | head -20
  else
    echo "(不存在)"
  fi
  log "磁盘"
  df -h / | tail -1
}

# ------------------------------------------------------------------ 轮转
rotate() {
  log "轮转：删除超过 $KEEP_DAYS 天的旧备份"
  [ -d "$OUT_ROOT" ] || return 0
  local cutoff found=0
  cutoff="$(date -d "$KEEP_DAYS days ago" +%s)"
  # 只匹配 YYYY-MM-DD 形式的目录，绝不碰别的文件
  while IFS= read -r d; do
    [ -n "$d" ] || continue
    local base ts
    base="$(basename "$d")"
    case "$base" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
      *) continue ;;
    esac
    ts="$(date -d "$base" +%s 2>/dev/null || echo 0)"
    if [ "$ts" -gt 0 ] && [ "$ts" -lt "$cutoff" ]; then
      found=1
      if [ "$DRY_RUN" = "1" ]; then
        echo "  [dry-run] 将删除 $d ($(du -sh "$d" 2>/dev/null | cut -f1))"
      else
        echo "  删除 $d ($(du -sh "$d" 2>/dev/null | cut -f1))"
        rm -rf -- "$d"
      fi
    fi
  done < <(find "$OUT_ROOT" -maxdepth 1 -mindepth 1 -type d | sort)
  [ "$found" = "0" ] && echo "  没有需要删除的旧备份"
  return 0
}

# ------------------------------------------------------------------ 主流程
do_backup() {
  local stamp dest
  stamp="$(date +%F)"
  dest="$OUT_ROOT/$stamp"

  if [ "$DRY_RUN" = "1" ]; then
    log "[dry-run] 备份将写到 $dest"
    echo "  bash $SCRIPT_DIR/backup-volumes.sh -o $dest"
    rotate
    return 0
  fi

  # 兜底：cron 不读 /etc/profile，显式给出 JDK/Maven 路径
  export JAVA_HOME="${JAVA_HOME:-/usr/local/jdk17}"
  export PATH="$JAVA_HOME/bin:/usr/local/maven/bin:$PATH"

  log "备份到 $dest"
  mkdir -p "$dest"
  if ! bash "$SCRIPT_DIR/backup-volumes.sh" -o "$dest"; then
    echo "!! 备份失败，保留现场以便排查: $dest" >&2
    exit 1
  fi

  log "更新 latest 软链"
  ln -sfn "$dest" "$OUT_ROOT/latest"
  echo "  latest -> $dest"

  rotate

  log "完成"
  echo "  本次: $dest ($(du -sh "$dest" | cut -f1))"
  echo "  总量: $OUT_ROOT ($(du -sh "$OUT_ROOT" 2>/dev/null | cut -f1))"
  df -h / | tail -1
}

case "$ACTION" in
  install)   do_install ;;
  uninstall) do_uninstall ;;
  status)    do_status ;;
  backup)    do_backup ;;
esac
