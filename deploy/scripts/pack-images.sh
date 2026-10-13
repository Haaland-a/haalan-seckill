#!/usr/bin/env bash
# ============================================================================
#  pack-images.sh —— 在【旧机器】把部署需要的镜像打包成可离线搬运的 tar.gz
#
#  用法:
#     bash scripts/pack-images.sh [-o 输出目录] [--force] [-h]
#
#  选项:
#     -o, --out DIR     输出目录（默认 /root/haalan-images）
#     --force           覆盖已存在的包（默认跳过已存在的）
#     -h, --help        显示本帮助
#
#  产出:
#     <out>/<镜像名>.tar.gz      每个镜像一个包（gzip -6）
#     <out>/MANIFEST.txt         镜像 -> 文件 映射 + 大小 + sha256
#     <out>/load-images.sh       拷到新机器后执行即可批量导入
#     <out>/SHA256SUMS           校验用
#
#  为什么按镜像分开打包，而不是 docker save 全部 | gzip > all.tar.gz:
#     1. 换机时往往只需要其中几个（比如只重建前端就不用搬 es/kibana）
#     2. 单个包坏了只重传那一个，不用整个 5 GB 重来
#     3. gzip 是流式的，一个包出错全部作废
#
#  注意: 本脚本只做 docker save，不删除任何镜像、不碰任何容器。
# ============================================================================
set -euo pipefail

OUT_DIR="/root/haalan-images"
FORCE=0

usage() { sed -n '2,25p' "$0"; exit 0; }

while [ $# -gt 0 ]; do
	case "$1" in
		-o|--out)   OUT_DIR="${2:?--out 需要参数}"; shift 2 ;;
		--force)    FORCE=1; shift ;;
		-h|--help)  usage ;;
		*) echo "未知参数: $1" >&2; usage ;;
	esac
done

# 部署全链路需要的镜像。顺序 = 中间件 -> 基础运行时 -> 应用基座
IMAGES=(
	"mysql:8.0.33"
	"redis:7"
	"nacos/nacos-server:v2.1.0-slim"
	"elasticsearch:7.12.1"
	"kibana:7.12.1"
	"rabbitmq:3.8-management"
	"seataio/seata-server:1.6.1"
	"xuxueli/xxl-job-admin:2.4.1"
	"btgoose/jdk17:latest"
	"node:22-alpine"
	"nginx:1.25-alpine"
	"haalan/sentinel-dashboard:1.8.6"
)

mkdir -p "$OUT_DIR"
cd "$OUT_DIR"

# 文件名里不能有 / 和 :
sanitize() { printf '%s' "$1" | sed 's|[/:]|_|g'; }

echo "输出目录: $OUT_DIR"
echo

MISSING=()
PACKED=0
SKIPPED=0

for img in "${IMAGES[@]}"; do
	file="$(sanitize "$img").tar.gz"

	if ! docker image inspect "$img" >/dev/null 2>&1; then
		printf '  %-38s [缺失] 本机没有这个镜像，跳过\n' "$img"
		MISSING+=("$img")
		continue
	fi

	if [ -s "$file" ] && [ "$FORCE" != "1" ]; then
		printf '  %-38s [已有] %s\n' "$img" "$(du -h "$file" | cut -f1)"
		SKIPPED=$((SKIPPED + 1))
		continue
	fi

	printf '  %-38s 打包中... ' "$img"
	# 管道直出 gzip，避免先落一个 4.8 GB 的中间 tar 占满磁盘
	if docker save "$img" | gzip -6 > "$file.tmp"; then
		mv -f "$file.tmp" "$file"
		printf '%s\n' "$(du -h "$file" | cut -f1)"
		PACKED=$((PACKED + 1))
	else
		rm -f "$file.tmp"
		echo "失败！"
		exit 1
	fi
done

echo
echo "==> 生成清单"

{
	echo "# haalan 离线镜像包清单"
	echo "# 生成时间: $(date -Iseconds)"
	echo "# 生成主机: $(hostname)"
	echo "#"
	echo "# 格式: 镜像名 <TAB> 文件名 <TAB> 大小 <TAB> sha256"
	for img in "${IMAGES[@]}"; do
		file="$(sanitize "$img").tar.gz"
		if [ -s "$file" ]; then
			printf '%s\t%s\t%s\t%s\n' \
				"$img" "$file" \
				"$(du -h "$file" | cut -f1)" \
				"$(sha256sum "$file" | cut -d' ' -f1)"
		else
			printf '%s\t%s\t%s\t%s\n' "$img" "-" "-" "缺失"
		fi
	done
} > MANIFEST.txt

# 新机器上的导入脚本
cat > load-images.sh <<'LOADER'
#!/usr/bin/env bash
# 在新机器上执行: bash load-images.sh [镜像名...]
# 不带参数 = 导入全部。需要和本脚本、*.tar.gz 放在同一目录。
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ $# -gt 0 ]; then
	files=()
	for want in "$@"; do
		f="$(printf '%s' "$want" | sed 's|[/:]|_|g').tar.gz"
		[ -s "$f" ] || { echo "没有这个包: $f" >&2; exit 1; }
		files+=("$f")
	done
else
	files=(*.tar.gz)
fi

for f in "${files[@]}"; do
	printf '%-40s 导入中... ' "$f"
	gunzip -c "$f" | docker load | tail -1
done
echo
echo "完成。当前镜像:"
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}' | sort
LOADER
chmod +x load-images.sh

sha256sum ./*.tar.gz > SHA256SUMS 2>/dev/null || true

TOTAL="$(du -sh "$OUT_DIR" | cut -f1)"
echo
echo "============================================"
echo " 打包完成: $PACKED 个新打包 / $SKIPPED 个已存在"
echo " 输出目录: $OUT_DIR  (合计 $TOTAL)"
echo " 清单:     $OUT_DIR/MANIFEST.txt"
echo " 校验:     (cd $OUT_DIR && sha256sum -c SHA256SUMS)"
echo
echo " 搬到新机器后:"
echo "   tar czf - -C $OUT_DIR . | ssh root@<新IP> 'mkdir -p /root/haalan-images && tar xzf - -C /root/haalan-images'"
echo "   ssh root@<新IP> 'cd /root/haalan-images && bash load-images.sh'"
if [ ${#MISSING[@]} -gt 0 ]; then
	echo
	echo " ⚠ 以下镜像本机没有，未打包: ${MISSING[*]}"
fi
echo "============================================"
