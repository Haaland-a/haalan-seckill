#!/usr/bin/env bash
#
# 把 ES 的 products 索引换成带 IK 分词器的版本。
#
# 为什么需要这个脚本：
#   ES 的索引 mapping 一旦建好就不能再改，分词器属于 mapping，所以
#   「给已有的 products 索引加 ik_max_word」这件事做不到原地完成，
#   只能「新建一个带 IK 的索引 + 把数据搬过去 + 换名字」。
#
# 本脚本的做法（全程不丢数据）：
#   1. 把现有 products 原样复制一份到 products_pre_ik（安全网，保留不删）
#   2. 删除 products
#   3. 用 IK mapping 重建 products
#   4. 从 products_pre_ik 把数据 reindex 回 products
#   5. 校验文档数一致、IK 分词生效
#
# 回滚：把 products 删掉，再把 products_pre_ik 复制回 products 即可
#       （脚本末尾会打印这条命令）。
#
# 用法：
#   bash scripts/reindex-products.sh              # 交互确认后执行
#   bash scripts/reindex-products.sh --dry-run    # 只检查，不改任何东西
#   bash scripts/reindex-products.sh -y           # 跳过确认
#   bash scripts/reindex-products.sh --es URL     # 指定 ES 地址（默认自动探测 localhost:9200 / es:9200）
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

INDEX="${INDEX_NAME:-products}"
BACKUP_INDEX="${BACKUP_INDEX_NAME:-products_pre_ik}"
# 默认按顺序探测：宿主机上 ES 端口映射在 localhost:9200；容器内用服务名 es:9200
ES_URL="${ES_URL:-}"
MAPPING_FILE="$DEPLOY_DIR/deploy-assets/es/products-mapping.json"
ASSUME_YES=0
DRY_RUN=0

usage() {
	sed -n '2,26p' "$0"
	exit 0
}

while [ $# -gt 0 ]; do
	case "$1" in
		-y|--yes)   ASSUME_YES=1; shift ;;
		--dry-run)  DRY_RUN=1; shift ;;
		--es)       ES_URL="$2"; shift 2 ;;
		-h|--help)  usage ;;
		*) echo "未知参数: $1" >&2; usage ;;
	esac
done

log()  { printf '\033[36m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m  ✓\033[0m %s\n' "$*"; }
warn() { printf '\033[33m  !\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

es() { curl -s -H 'Content-Type: application/json' "$@"; }

# 优先用宿主机的 curl；没有就走 es 容器里的 curl（CentOS 7 上一般都有）
if ! command -v curl >/dev/null 2>&1; then
	die "找不到 curl"
fi

# 没显式给 --es 就自己探测一个能连上的地址
if [ -z "$ES_URL" ]; then
	for candidate in http://localhost:9200 http://es:9200 http://127.0.0.1:9200; do
		if curl -s -m 3 "$candidate" | grep -q '"cluster_name"'; then
			ES_URL="$candidate"
			break
		fi
	done
	[ -n "$ES_URL" ] || die "探测不到 ES。请用 --es http://<host>:9200 显式指定。"
fi

log "ES 地址: $ES_URL"
log "目标索引: $INDEX"
log "备份索引: $BACKUP_INDEX"
echo

# ---------------------------------------------------------------- 前置检查
log "1/7 前置检查"

if ! es "$ES_URL" >/dev/null 2>&1; then
	die "连不上 ES（$ES_URL）。如果是容器内执行，注意用容器名 es 而不是 localhost。"
fi
ok "ES 可达"

CLUSTER_STATUS="$(es "$ES_URL/_cluster/health" | grep -o '"status":"[a-z]*"' | head -1 | cut -d'"' -f4)"
ok "集群状态: ${CLUSTER_STATUS:-未知}"

if ! es "$ES_URL/_cat/plugins" 2>/dev/null | grep -q analysis-ik; then
	die "ES 上没有安装 IK 插件（analysis-ik）。请先按 deploy/README.md 第 6.7 节安装并重启 es。"
fi
ok "IK 插件已安装: $(es "$ES_URL/_cat/plugins?h=component" | tr -d ' ')"

if [ ! -f "$MAPPING_FILE" ]; then
	die "找不到 mapping 文件: $MAPPING_FILE"
fi
ok "mapping 文件存在"

if ! es "$ES_URL/$INDEX" | grep -q '"mappings"'; then
	die "索引 $INDEX 不存在。若 item-service 已启动过，它应该会自动创建；否则请先启动 item-service。"
fi

BEFORE_COUNT="$(es "$ES_URL/$INDEX/_count" | grep -o '"count":[0-9]*' | cut -d: -f2)"
ok "索引 $INDEX 当前文档数: $BEFORE_COUNT"

CURRENT_HAS_IK="$(es "$ES_URL/$INDEX/_mapping" | grep -c 'ik_max_word' || true)"
if [ "$CURRENT_HAS_IK" -gt 0 ]; then
	warn "索引 $INDEX 的 mapping 里已经出现 ik_max_word，可能已经换过了。"
	warn "继续执行会把它重建一遍（数据不丢，但没必要）。"
fi

if [ "$DRY_RUN" = "1" ]; then
	echo
	log "--dry-run 结束：什么都没改。"
	echo "  真正执行时会做："
	echo "    1. 复制 $INDEX → $BACKUP_INDEX（保留，作为回滚点）"
	echo "    2. 删除 $INDEX"
	echo "    3. 用 $MAPPING_FILE 重建 $INDEX"
	echo "    4. 复制 $BACKUP_INDEX → $INDEX"
	echo "    5. 校验文档数与分词效果"
	exit 0
fi

if [ "$ASSUME_YES" != "1" ]; then
	echo
	echo "即将执行以下操作（索引 $INDEX 会被删除并重建，原数据先备份到 $BACKUP_INDEX）："
	echo "  1. 复制 $INDEX → $BACKUP_INDEX"
	echo "  2. 删除 $INDEX"
	echo "  3. 用 IK mapping 重建 $INDEX"
	echo "  4. 复制 $BACKUP_INDEX → $INDEX"
	echo
	printf '确认继续？输入 yes 回车：'
	read -r answer
	[ "$answer" = "yes" ] || die "已取消，什么都没改。"
fi

# ---------------------------------------------------- 1. 备份到 products_pre_ik
log "2/7 复制 $INDEX → $BACKUP_INDEX（原索引一个字节都不动）"

if es "$ES_URL/$BACKUP_INDEX" | grep -q '"mappings"'; then
	warn "$BACKUP_INDEX 已存在，先删掉旧的备份索引"
	es -X DELETE "$ES_URL/$BACKUP_INDEX" >/dev/null
fi

es -X POST "$ES_URL/_reindex?wait_for_completion=true&refresh=true" -d "{
  \"source\": { \"index\": \"$INDEX\" },
  \"dest\":   { \"index\": \"$BACKUP_INDEX\" }
}" | grep -o '"total":[0-9]*' | head -1

BACKUP_COUNT="$(es "$ES_URL/$BACKUP_INDEX/_count" | grep -o '"count":[0-9]*' | cut -d: -f2)"
[ "$BACKUP_COUNT" = "$BEFORE_COUNT" ] || die "备份索引文档数（$BACKUP_COUNT）与源索引（$BEFORE_COUNT）不一致，已中止，未做任何破坏性操作。"
ok "备份完成，$BACKUP_INDEX 有 $BACKUP_COUNT 条文档"

# --------------------------------------------------------------- 2. 重建索引
log "3/7 删除旧 $INDEX 并用 IK mapping 重建"
es -X DELETE "$ES_URL/$INDEX" >/dev/null
ok "旧索引已删除（数据在 $BACKUP_INDEX 里）"

CREATE_RESP="$(es -X PUT "$ES_URL/$INDEX" --data-binary "@$MAPPING_FILE")"
case "$CREATE_RESP" in
	*'"acknowledged":true'*) ok "新索引已建好（ik_max_word + ik_smart）" ;;
	*)
		printf '\033[31m[ERROR]\033[0m 用 IK mapping 建索引失败，ES 返回：\n%s\n' "$CREATE_RESP" >&2
		echo >&2
		echo "数据仍在 $BACKUP_INDEX 里，没有丢失。修好问题后可以这样恢复：" >&2
		echo "  curl -X DELETE '$ES_URL/$INDEX'" >&2
		echo "  curl -X POST '$ES_URL/_reindex?wait_for_completion=true' -H 'Content-Type: application/json' -d '{\"source\":{\"index\":\"$BACKUP_INDEX\"},\"dest\":{\"index\":\"$INDEX\"}}'" >&2
		exit 1
		;;
esac

# --------------------------------------------------------------- 3. 数据回灌
log "4/7 复制 $BACKUP_INDEX → $INDEX"
es -X POST "$ES_URL/_reindex?wait_for_completion=true&refresh=true" -d "{
  \"source\": { \"index\": \"$BACKUP_INDEX\" },
  \"dest\":   { \"index\": \"$INDEX\" }
}" | grep -o '"total":[0-9]*' | head -1

AFTER_COUNT="$(es "$ES_URL/$INDEX/_count" | grep -o '"count":[0-9]*' | cut -d: -f2)"
[ "$AFTER_COUNT" = "$BEFORE_COUNT" ] || die "回灌后文档数（$AFTER_COUNT）与原来（$BEFORE_COUNT）不一致！原数据仍在 $BACKUP_INDEX，请勿删除。"
ok "数据已回灌，文档数 $AFTER_COUNT（与原来一致）"

# ------------------------------------------------------------------ 4. 校验
log "5/7 校验 mapping"
if es "$ES_URL/$INDEX/_mapping" | grep -q 'ik_max_word'; then
	ok "mapping 已带 ik_max_word"
else
	die "mapping 里没找到 ik_max_word，重建可能没生效。"
fi

log "6/7 校验分词效果"
echo -n "  ik_max_word : "
es -X POST "$ES_URL/$INDEX/_analyze" -d '{"analyzer":"ik_max_word","text":"华为手机秒杀活动"}' \
	| tr ',' '\n' | grep '"token"' | sed 's/.*: *//' | tr -d '"' | tr '\n' ' '
echo
echo -n "  ik_smart    : "
es -X POST "$ES_URL/$INDEX/_analyze" -d '{"analyzer":"ik_smart","text":"华为手机秒杀活动"}' \
	| tr ',' '\n' | grep '"token"' | sed 's/.*: *//' | tr -d '"' | tr '\n' ' '
echo

log "7/7 用真实数据试搜"
SAMPLE="$(es "$ES_URL/$INDEX/_search?size=1" | grep -o '"spuName":"[^"]*"' | head -1 | cut -d'"' -f4 || true)"
if [ -n "$SAMPLE" ]; then
	# 取商品名的前两个字当关键词
	KEYWORD="$(printf '%s' "$SAMPLE" | cut -c1-2)"
	HITS="$(es -X POST "$ES_URL/$INDEX/_search" -d "{\"query\":{\"match\":{\"spuName\":\"$KEYWORD\"}}}" \
		| grep -o '"total":{"value":[0-9]*' | head -1 | cut -d: -f2)"
	ok "样例商品「$SAMPLE」→ 用关键词「$KEYWORD」搜到 $HITS 条"
fi

echo
printf '\033[32m完成。\033[0m\n'
echo
echo "回滚方法（把索引换回 IK 之前的版本）："
echo "  curl -X DELETE '$ES_URL/$INDEX'"
echo "  curl -X POST '$ES_URL/_reindex?wait_for_completion=true' -H 'Content-Type: application/json' -d '{\"source\":{\"index\":\"$BACKUP_INDEX\"},\"dest\":{\"index\":\"$INDEX\"}}'"
echo
echo "确认无误、观察一段时间后，可以删掉备份索引释放空间："
echo "  curl -X DELETE '$ES_URL/$BACKUP_INDEX'"
