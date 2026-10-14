---
name: haalan-deploy
description: haalan 秒杀商城（后端 haalan / 前端 haalan_ / 管理端 haalan-admin）的 Docker 部署与运维。当用户提到 haalan 部署、hd/hs 命令、192.168.100.133、容器起不来、搜索没结果、Sentinel、Nacos 配置、数据卷迁移换服务器、服务器时钟、或「改完代码怎么上线」时使用。包含实测故障排查手册。
version: v1.0
---

# haalan 部署运维

> 完整手册：`D:\dswork\work1\haalan\deploy\README.md`（1203 行，9 节，全部数字来自实测）
> 本 skill 是**操作入口 + 故障排查手册**；细节一律回查 README 对应章节。

---

## ⛔ 硬约束（用户明令，任何操作前先确认没违反）

1. **不许删除任何文件**
2. **不许关闭、修改服务器上任何容器**（重启 es 等需单独授权）
3. **不许修改网络**
4. **不许删服务器文件**

> 判定口诀：`docker rm/rmi/stop/restart/prune`、`docker network` 改动、动中间件容器 —— 全部属于「要确认」。
> 只重建 8 个**应用**容器（gateway / user-service / item-service / order-service / search-service / seckill-service / frontend / admin）是允许的，这是日常操作。

---

## 一、两条快捷命令（本机，已配好）

```powershell
hs                          # SSH 登录服务器（密钥免密）
hs "docker ps"              # 跑一条命令
hd                          # 同步本机源码 → 服务器编译 → 重建全部 8 个应用
hd gateway                  # 只重建 gateway
hd gateway admin            # 重建指定的几个
hd -NoSync user-service     # 不同步源码，用服务器现有源码重建
hd -NoBuild gateway         # 跳过 mvn，用现有 jar 重建镜像
hd -SyncOnly                # 只同步源码，不编译不部署
hd -Status                  # 只看容器状态
hd -List                    # 列出可用服务名
```

- `hs.ps1` / `hs.cmd` → `C:\Users\Haaland\.local\bin\`
- `hd.ps1` / `hd.cmd` → 同上；内部调 `D:\dswork\work1\haalan\deploy\scripts\sync-source.ps1`

---

## 二、环境事实（实测，非推测）

| 项 | 值 |
|---|---|
| 服务器 | `192.168.100.133`，root，密码 `root`，密钥已授权免密 |
| SSH 别名 | `ssh haalan` / `ssh 133`（`C:\Users\Haaland\.ssh\config`） |
| 系统 | CentOS 7，内核 3.10，**2 核**，内存 7.8 GB（可用约 1.5 GB，**紧张**） |
| Docker | 26.1.4，Compose v2.27.1，buildx v0.14.1 |
| JDK / Maven | `/usr/local/jdk17`（17.0.2）/ `/usr/local/maven`（3.9.6） |
| 部署根目录 | `/root/haalan`（后端）、`/root/haalan_`、`/root/haalan-admin` |
| 部署工具 | `/root/haalan/deploy/` |
| 容器 | **17 个**：8 中间件 + 8 应用 + 1 工具（sentinel-dashboard） |
| 网络 | `root_haalan`（全部容器共用） |
| compose 项目 | `root`（由 `deploy/.env` 的 `COMPOSE_PROJECT_NAME=root` 决定） |

**出网情况**：阿里云 Maven ✅ / npmjs ✅ / Docker Hub 走 `daemon.json` 加速器 ✅ / **github.com ❌ 连接被拒**（所以服务器不能 `git pull`）。

**本机**：无 Docker；有 `mvn`（`D:\My flie\apache-maven-3.9.10-bin\...`）、`java 17.0.11`、`node v24.1.0`、`git`。本机直连 github 也不通，push 要走代理：
```powershell
git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push origin master
```

---

## 三、日常工作流

### 3.1 改后端代码 → 上线

```powershell
hd user-service        # 一条命令搞定
```

内部 4 步：
1. 本机打包源码（约 170 KB，排除 target/.git/node_modules）→ scp
2. 服务器解包覆盖 `/root/haalan`（**这一步最容易被忽略 —— 不做的话服务器编译的还是旧代码**）
3. 服务器 `mvn -o clean package -DskipTests`（离线，约 26 秒）
4. `docker compose -f docker-compose.apps.yml up -d <服务>`（只动指定容器）

### 3.2 改前端

```powershell
hd frontend            # 或 hd admin
```
前端不走 mvn，是容器内 `npm ci && npm run build`。

### 3.3 验证

```powershell
hs "docker ps --format '{{.Names}} | {{.Status}}' | sort"
hs "curl -s -o /dev/null -w '%{http_code}' http://localhost/"          # 期望 200
hs "curl -s -o /dev/null -w '%{http_code}' http://localhost:81/"        # 期望 200
hs "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/user/info"  # 期望 401
```

---

## 四、⚠️ 最容易踩的 5 个坑

### 4.1 「部署了但没生效」——源码没同步

`deploy.sh` 在**服务器上**跑 mvn，编译的是 `/root/haalan` 里的源码。
只跑 `hs "cd /root/haalan/deploy && bash scripts/deploy.sh gateway"` 而**没有先同步源码**，编译的是服务器上的旧代码。

→ **永远用 `hd <服务名>`，不要手敲 deploy.sh**（除非刚同步过）。

### 4.2 ⛔ 绝不要在现有 VM 跑完整版 compose

```bash
# 危险！会把 8 个中间件全部 Recreate
docker compose -f docker-compose.yml up -d
```

实测 dry-run 输出 8 个中间件全是 `Recreate`。**日常只用 `docker-compose.apps.yml`**。

### 4.3 ⛔ 绝不要加 `--remove-orphans`

会删掉那 8 个中间件（它们对 apps 编排来说是 orphan）。`deploy.sh` 已刻意不加，别"顺手补上"。

### 4.4 ⛔ 不要删 `/root/docker-compose.yml`

还有 12 个容器（8 中间件 + gateway/user-service/frontend/admin）靠它被 compose 认领。
删了它们下次操作会被当孤儿。**统一编排需要停机窗口，未做。**

### 4.5 容器名冲突（search-service 历史遗留）

`search-service` 原本属于 `haalan` 项目（镜像 `haalan-search-service`，双网络）。
apps 编排（项目 `root`）会去 **Creating** 而非 Recreate → 报 `Conflict. The container name "/search-service" is already in use`。

**已于 2026-09-02 归队**（现为 `root-search-service:latest`，单网络）。若再遇到：
```bash
hs "docker inspect search-service > /root/search-service-old-inspect.json"   # 先存回滚依据
hs "docker rm -f search-service"                                             # 应用容器，允许
hs "cd /root/haalan/deploy && docker compose -f docker-compose.apps.yml up -d search-service"
```

---

## 五、故障排查手册（全部实测过）

### 5.1 搜索接口 500 / Connection refused —— ES 端口没写

**症状**：`GET /api/product/search?keyword=华为` → `{"code":500,...}`；日志 `java.net.ConnectException: Connection refused`。

**根因**：6 个服务的 `application-dev.yaml` 里 `haalan.es.host: es`（**不带端口**）。
`EsProperties.host` 直接交给 `HttpHost.create()`，端口为 -1 → Apache HttpClient 退化成 **http 默认端口 80**，而 ES 在 9200。

**修复**：6 个 `application-dev.yaml` 全部改为 `host: es:9200`
（item-service / search-service / order-service / seckill-service / user-service / haalan-gateway）

### 5.2 Sentinel 控制台看不到应用 —— dashboard 地址拼错

**症状**：日志 `WARNING Parse port of dashboard server failed: 192.168.100.133:8090:8090` + `Dashboard server address not configured or not available`。

**根因**：`bootstrap.yaml` 里 `transport.dashboard: ${haalan.sentinel.transport.host:localhost}:8090` 已经带了 `:8090`，而 `application-dev.yaml` 的 host 又写了 `192.168.100.133:8090` → 拼成两个冒号。Sentinel 的 `TransportConfig` 只接受**恰好一个冒号**的地址，直接丢弃。

**修复**：`application-dev.yaml` 里 `haalan.sentinel.transport.host: sentinel-dashboard`（**只填主机名，不带端口**）。

**会出现在控制台的只有 4 个服务**：item / order / search / seckill（它们依赖 `haalan-api`，而 `haalan-api/pom.xml` 有 `spring-cloud-starter-alibaba-sentinel`）。gateway 与 user-service 只依赖 `haalan-common`，**没有 sentinel，看不到是设计使然**。

**验证连通**（REST API 要登录，返回 401 是正常的）：
```bash
hs "for s in item-service order-service search-service seckill-service; do printf '%-16s %s\n' \$s \$(docker exec \$s grep -c 'Socket income' /root/logs/csp/command-center.log.* 2>/dev/null | head -1); done"
```
访问 UI：`http://192.168.100.133:8090`，账号密码均 `sentinel`。

**⚠️ 不要用 `docker compose --profile tools up -d sentinel-dashboard`** —— compose 起服务前会先 pull，服务器直连 registry-1.docker.io DNS 失败，且 compose 的 pull 路径**不走** `daemon.json` 的加速器。用 `bash scripts/deploy-sentinel.sh build|up|status`（内部用 `docker run`）。

### 5.3 Sentinel 容器起不来 —— JDK 17 兼容性

Sentinel 1.8.6 是 JDK8 编译的 Spring Boot 2.5.12。JDK17 下必须加 10 个 `--add-opens`，否则抛 `InaccessibleObjectException`。
见 `deploy-assets/sentinel/Dockerfile` 里的 `JAVA_OPTS`，**不要删**。

### 5.4 中文搜索效果差 —— IK 装了但索引没用上

**关键认知**：ES 索引 mapping 的 analyzer **建好后不能原地修改**。

已完成的部分：
- IK 插件已装（`docker exec es bin/elasticsearch-plugin list` → `analysis-ik`）
- `products` 索引已重建为 IK mapping（`analyzer: ik_max_word` / `search_analyzer: ik_smart`）
- 安全网索引 `products_pre_ik` 保留着

**但代码里没有任何 `ik_max_word` 引用** —— 索引是 `ProductIndexInitializer.java` 启动时按 `deploy-assets/es/products-mapping.json` 建的。
搜索侧走 `QueryBuilders.matchQuery("spuName", keyword)`，用索引自身分词器，**所以 mapping 对了就生效**。

要再调优（multi_match + 字段权重）见 README 9.7。重建索引用 `bash scripts/reindex-products.sh`。

### 5.5 内存不够 / 容器被 OOM

内存只有 7.8 GB，可用约 1.5 GB。占用大头（`docker stats` 实测）：
nacos 656M / es 616M / mysql8 418M / sentinel-dashboard 232M / kibana 206M / mq 68M。

缓解：`JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m`（已配）+ 日志轮转（已配）。
实在不够可停 kibana（省 206M），**但属「修改容器」需确认**。

### 5.6 服务器时间不对 / tar 刷「时间戳是未来的 N 秒之后」

**根因**：`/etc/chrony.conf` 里 `makestep 1.0 3` —— `3` 表示**只在 chronyd 启动后的前 3 次校时允许跳变**，之后只能缓慢 slew。VM 被挂起过就会永久追不上。

**修复（已做过，2026-10-14）**：
```bash
chronyc makestep                                              # 立刻跳变
sed -i 's|^makestep 1.0 3$|makestep 1.0 -1|' /etc/chrony.conf # -1 = 任何时候偏移>1秒就跳
systemctl restart chronyd
```

**⚠️ 跳变的副作用**：Redis 带 TTL 的 key 会**不可逆过期**（上次 25 个 key 掉了 23 个）。
跳变前先 `BGSAVE` 并记下 key 清单。详见 README 6.10。

### 5.7 非登录 shell 找不到 java/mvn

`ssh` 执行单条命令时**不加载 `/etc/profile`**，所以会出现「装了 JDK 却报 `java: 未找到命令`」。

**已修**（2026-09-02）：
- `/etc/profile.d/java.sh`（设 JAVA_HOME/MAVEN_HOME/PATH）
- `/usr/local/bin/` 下的软链 `java`/`javac`/`mvn` 等（`/usr/local/bin` 在默认 PATH 里，非登录 shell 也能命中）

若脚本里仍要保险，显式加：
```bash
export JAVA_HOME=/usr/local/jdk17
export PATH="$JAVA_HOME/bin:/usr/local/maven/bin:$PATH"
```

### 5.8 Nacos 健康检查是假的

原编排是 `test: ["CMD", "true"]` —— 永远返回健康，形同虚设。
新编排 `deploy/docker-compose.yml` 已换成真实探针：
`test: ["CMD","curl","-sf","http://localhost:8848/nacos/v1/console/health/readiness"]`

**现有 VM 上的 nacos 容器仍是旧配置**（要生效需重建，属「修改容器」故未做）。新机器从零部署自动是新探针。

---

## 六、换服务器 / 数据卷迁移

> 详细步骤见 README 第七节。核心要点：

**三类数据**：
| 位置 | 内容 | 迁移方式 |
|---|---|---|
| Docker 卷 6 个 | redis-data / es-data / es-plugins / mq-data / mq-plugins / nacos-data | tar 打包 |
| 绑定 `/data/mysql8` | 7 个业务库 | **mysqldump**（不用 tar —— 456MB InnoDB 热拷不一致，且跨小版本不兼容） |
| 绑定 `/root/seata` | seata 配置 | tar |

**流程**：
```bash
# 旧机
bash scripts/backup-volumes.sh -o /root/haalan-backup     # 产出 25 MB
sha256sum -c SHA256SUMS
rsync -aP /root/haalan-backup/ root@<新IP>:/root/haalan-backup/

# 新机：改 .env（最关键！）
#   COMPOSE_PROJECT_NAME=        ← 必须留空
#   HOST_IP=<新机IP>             ← 必改
bash scripts/restore-volumes.sh -i /root/haalan-backup --dry-run
bash scripts/restore-volumes.sh -i /root/haalan-backup
```

**⚠️ 为什么 `COMPOSE_PROJECT_NAME` 必须留空**：旧机刻意设成 `root` 是为了复用**已存在**的 `root_haalan` 网络与 `root_*` 卷。新机上这些资源不存在，留着会建一套同名空卷，数据对不上。

**⚠️ 属主必须改回去**（还原脚本按 `MANIFEST.txt` 自动做）：
Redis/RabbitMQ 卷 `999:999`、ES 卷 `1000:0`、Nacos 卷 `0:0`。
**漏掉 chown 的症状：容器启动即报权限错误。**

**⚠️ RabbitMQ 的 mnesia 节点名是 `rabbit@mq`**（由 `container_name` 决定）→ 换机时**容器名必须仍叫 `mq`**，否则读不出旧数据。

**压缩率反直觉**（别以为备份没成功）：mq-data 349 MB → 3.4 MB；nacos-data 457 MB → 464 KB（几乎全是 `protocol/` 里的零字节 raft 日志）。

**三个坑**：
1. 不要先 `up -d` 再还原数据（空卷启动会初始化空库，再灌数据可能重复）
2. 备份目录别放 `/root/haalan` 里面（`hd` 同步会覆盖）—— 放 `/root/haalan-backup`
3. 定时备份已配好：`crontab -e` 里每周日 03:30，保留 14 天（`scripts/cron-backup.sh --install/--status/--uninstall`）

---

## 七、离线镜像包（断网机器）

```bash
bash scripts/pack-images.sh -o /root/haalan-images    # 12 个镜像 → 2.3 GB
# 新机
bash load-images.sh                                   # 全部导入
bash load-images.sh mysql:8.0.33 redis:7               # 只导指定
```
产出 `MANIFEST.txt` + `load-images.sh` + `SHA256SUMS`。按镜像分开打包（不是单个大包）——换机常只需几个，且单包坏了只重传一个。

---

## 八、文件清单

**服务器** `/root/haalan/deploy/`：
```
README.md                      52989  完整手册（9 节）
.env / .env.example                    项目名、IP、各口令、JAVA_TOOL_OPTIONS
docker-compose.yml                     完整编排（中间件+应用+sentinel）⚠️ 勿在现有 VM up -d
docker-compose.apps.yml                应用层编排 ← 日常唯一入口
scripts/
  deploy.sh                            服务器侧：mvn + compose build/up
  deploy.ps1                           本机侧：mvn + scp jar
  sync-source.ps1                      本机侧：打包源码 → scp → 解包
  deploy-sentinel.sh                   Sentinel build/up/status/logs/down
  backup-volumes.sh                    备份数据（旧机）
  restore-volumes.sh                   还原数据（新机）
  cron-backup.sh                       定时备份 + 按天轮转
  pack-images.sh                       离线镜像打包
  reindex-products.sh                  重建 products 索引（换 IK mapping）
deploy-assets/
  sql/01-haalan.sql 02-xxl-job.sql 03-seata-at.sql 04-seata-tc.sql
  nacos/custom.env  nacos/nacos_config_export.zip（14 条配置）
  seata/application.yml
  es/elasticsearch-analysis-ik-7.12.1.zip  es/products-mapping.json
  sentinel/{Dockerfile, sentinel-dashboard.jar, README.md, run-sentinel-dashboard.txt}
  docker-compose.reference.yml
```

**本机**：`D:\dswork\work1\haalan\`（后端仓库）、`D:\dswork\work1\haalan_\`（前端）、`D:\dswork\work1\haalan-admin\`（管理端）

---

## 九、提交 git 的注意事项

仓库 `D:\dswork\work1\haalan` 有**预先存在的暂存项** `AD "Parent directory.zip"`（170 MB，文件缺失）。

**⛔ 提交必须带 pathspec**，否则会把 170 MB 的 zip 一起提交：
```powershell
git commit -m "..." -- deploy item-service search-service
```

push 走代理：
```powershell
git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push origin master
```

`deploy/` 下有些文件被 `.gitignore` 排除（`*.jar`、`*.zip`、`.env`），需 `git add -f`。

---

## 十、待办（未完成，需用户确认）

| 项 | 说明 |
|---|---|
| user-service 上线 | **已确认仍成立（2026-10-14 复查）**：镜像 `root-user-service:latest` 构建于 `2026-07-30T10:06:46`，容器内 `/app/app.jar` 也是 `2026-07-30 02:03`；而源码 `/root/haalan/user-service/.../LoginLogAspect.java`（2026-09-01 17:17）与 `TUserServiceImpl.java`（2026-09-01 22:25）都是 9 月的改动 → **改动确实没上线**。修法：`hd -NoSync user-service`（源码已在服务器上，不需再同步） |
| 清理 57 个 `<none>` 悬空镜像 | 可回收 5.7 GB，属删除操作 |
| 彻底统一编排 | 需一次「停起全部容器」的窗口 |
| products 之外的中文搜索调优 | multi_match + 字段权重，属功能改进 |
| 本机 deploy.ps1 的 SSH 凭据 | 建议换公钥，去掉 ASKPASS |

---

## 十一、虚拟机重启后要检查什么

VM 关机再开（或挂起恢复）后，按顺序过一遍：

```powershell
hs "date"                                                   # 1. 时间对不对（见 5.6）
hs "docker ps --format '{{.Names}}' | wc -l"                # 2. 应输出 17
hs "docker ps --format '{{.Names}} | {{.Status}}' | sort"   # 3. 有无 Exited
hs "curl -s 'http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100'"  # 4. 应有 7 个服务
hs "curl -s -o /dev/null -w '%{http_code}' http://localhost/"        # 5. 200
hs "curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/"   # 6. 200
hs "docker exec redis redis-cli --no-auth-warning -a 123456 DBSIZE"  # 7. 记录 key 数
```

- 容器都是 `restart: always`，正常情况下会自己起来。
- **`sentinel-dashboard` 是 `docker run` 起的**（不归 compose 管），也是 `--restart always`，会自己起来。若没起来：`bash scripts/deploy-sentinel.sh up`。
- **时钟是重点**：VM 挂起恢复后时钟会偏，`makestep 1.0 -1` 已配好，等 chronyd 自愈即可（`timedatectl | grep synchronized` 应变成 yes）。若仍偏，`chronyc makestep` —— **但先给 Redis 落盘并记 key 清单**（跳变会让带 TTL 的 key 不可逆过期）。
- xxl-job 在时间跳变瞬间会记 `schedule misfire`，属正常（策略是 DO_NOTHING，不补跑）。

---

## 十二、给用户汇报时的注意事项

- 所有数字**实测**，不要凭记忆。不确定就 `hs` 查。
- 涉及「修改容器 / 删文件 / 改网络」时**先说明再问**，不要直接做。
- 前端在 `:80`，管理端 `:81`，网关 `:8080`，Nacos `:8848`，Sentinel `:8090`，xxl-job `:8079`，ES `:9200`，Kibana `:5601`。
- 网关的 `/api/**` 返回 **401 是正常的**（JWT 拦截器在工作），不是故障。
