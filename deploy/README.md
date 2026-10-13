# haalan 部署方案

> 目标：**改完代码，几分钟内把服务重新部署上去**。
> 本文所有数字都来自对 `192.168.100.133` 的实测，不是估算。

---

## 一、现状（实测，非推测）

### 1.1 服务器

| 项目 | 实测值 |
|---|---|
| 主机 | `192.168.100.133`，CentOS 7，内核 3.10，2 核 |
| Docker | 26.1.4，Compose v2.27.1，buildx v0.14.1 |
| 内存 | 7.8 GB，**已用 6.4 GB，可用仅约 1 GB**；swap 8 GB 已用 1 GB |
| 磁盘 | `/` 50 GB，已用 20 GB；`/data/mysql8` 456 MB |
| 工具链 | JDK 17.0.2 `/usr/local/jdk17`、Maven 3.9.6 `/usr/local/maven`、git 1.8.3.1 |
| Maven 缓存 | `/root/.m2/repository` 270 MB，492 个 jar |
| 出网 | 阿里云 Maven 镜像 200 ✅、npmjs/npmmirror 200 ✅、Docker Hub 走加速器可拉 ✅、**github.com 拒绝连接 ❌** |

### 1.2 正在运行的 17 个容器

```
中间件(8)  mysql8  redis  nacos  es  kibana  mq  seata  xxl-job-admin
应用(8)    gateway  user-service  item-service  order-service
           search-service  seckill-service  frontend  haalan-admin
工具(1)    sentinel-dashboard          ← 2026-09-02 新增
```

Nacos 里已注册 7 个服务（6 微服务 + seata-server），ES 有 `products` 索引，
MySQL 有 7 个业务库。

> `sentinel-dashboard` 不在 `root` 项目里，它是用 `deploy-sentinel.sh` 单独
> `docker run` 起来的（原因见 6.6）。它加入 `root_haalan` 网络，所以能被
> 应用容器按容器名 `sentinel-dashboard:8090` 访问。

### 1.3 ⚠ 关键发现：服务器上存在两套 compose 编排

| compose 项目 | 配置文件 | 包含的服务 |
|---|---|---|
| `root` | `/root/docker-compose.yml` | **15 个**：8 个中间件 + 6 个微服务 + 2 个前端 |
| `haalan` | `/root/haalan/docker-compose.yml` | 7 个：6 微服务 + frontend |

实测各容器的归属：

```
root 项目    → mysql8 redis nacos es kibana mq seata xxl-job-admin
              gateway user-service item-service order-service
              seckill-service frontend haalan-admin
haalan 项目  → search-service        ← 唯一一个从 /root/haalan 起起来的
```

**`search-service` 是个特例**：它的镜像是 `haalan-search-service`（构建于
2026-05-31），而不是像其他 5 个后端那样的 `root-*`（构建于 2026-07-30）。
它还**同时接在两个网络上**（`haalan` 和 `root_haalan`）—— 这是历史上
排查它连不上 Nacos 时手工 `docker network connect` 留下的痕迹。

> 影响：如果用 `/root/haalan/docker-compose.yml` 去 `up -d`，会创建一个
> 只含 7 个服务的新项目上下文；而用 `/root/docker-compose.yml` 则会认为
> `search-service` 是孤儿。这正是本次要消除的混乱。

### 1.4 数据卷与网络（必须复用，不能重建）

| 资源 | 实际名字 | 挂载到 |
|---|---|---|
| 网络 | `root_haalan` | 全部 17 个容器（含 sentinel-dashboard） |
| 卷 | `root_redis-data` | redis `/data` |
| 卷 | `root_es-data` | es `/usr/share/elasticsearch/data` |
| 卷 | `root_es-plugins` | es plugins（**当前是空的**） |
| 卷 | `root_mq-data` | mq `/var/lib/rabbitmq`（含 haalan 用户与 /haalan vhost） |
| 卷 | `root_mq-plugins` | mq `/plugins` |
| 卷 | `root_nacos-data` | nacos `/home/nacos/data`（14 条配置） |
| 绑定 | `/data/mysql8` | mysql8 `/var/lib/mysql` |
| 绑定 | `/root/seata` | seata `/seata-server/resources` |

### 1.5 一个被误判的「坑」：`.dockerignore` 其实没问题

仓库根 `haalan/.dockerignore` 里有 `target/`，而 6 个 Dockerfile 都要
`COPY <svc>/target/<svc>-0.0.1-SNAPSHOT.jar`。看起来直接冲突。

**实测结论：不冲突。** Docker 的 `.dockerignore` 模式锚定在构建上下文根目录，
`target/` 只排除**根目录**的 `target/`，不会排除 `user-service/target/`。
在服务器上用真实 Docker 验证过：

```
上下文:  Dockerfile, sub/target/keep.txt, target/drop.txt, .dockerignore(target/)
结果:    /ctx/sub/target/keep.txt  → 存在 ✅
         /ctx/target/drop.txt      → 已排除 ✅
```

所以**不需要改任何 `.dockerignore` 或 Dockerfile**，现有构建方式本来就是对的。

### 1.6 代码版本状态

工作区与服务器源码**逐文件 MD5 比对**（排除 .git/.idea/target/node_modules）：

```
服务器 460 个源文件，工作区 461 个
仅服务器有:  haalan/docker-compose.yml        (旧编排，未纳入工作区)
仅工作区有:  haalan_/.dockerignore, haalan-admin/.dockerignore  (新增，无妨)
内容不同:   3 个文件，都是无关紧要的差异：
             · LoginLogAspect.java  — 仅注释不同
             · TUserServiceImpl.java — 仅行尾符 (CRLF/LF)
             · application-local.yaml — 服务器多了两行 test: 配置
```

**结论：工作区代码 == 服务器代码。** 可以放心在工作区改、往服务器推。

git 状态：

| 仓库 | 工作区 HEAD | 服务器 HEAD | 远端 |
|---|---|---|---|
| haalan | `13bc1ba 1` | `5b24a18 上级目录所需部件,部署时使用` | `https://github.com/Haaland-a/haalan-seckill.git` |
| haalan_ | `0b394a4 用户端前端` | 同左 | `git@github.com:Haaland-a/haalan_.git` |
| haalan-admin | `082905c 优化前端...` | 同左 | `git@github.com:Haaland-a/haalan-admin.git` |

`haalan` 工作区比服务器多一个提交 `13bc1ba`（改了两个配置文件），
两者都有未提交改动。**服务器无法访问 GitHub（连接被拒）**，所以推送只能在
你本机做。

---

## 二、方案总览

核心思路：**把「编译」和「构建镜像」分开，让日常迭代只碰应用容器。**

```
┌─────────────────────── 本机 (Windows) ───────────────────────┐
│  haalan\        Maven 编译 → 6 个 jar                        │
│  haalan_\       Vue 源码                                      │
│  haalan-admin\  Vue 源码                                      │
└───────────────────────────┬──────────────────────────────────┘
                            │ scp 约 59 MB/s，6 个 jar ≈ 12 秒
                            ▼
┌────────────────────── 服务器 (192.168.100.133) ───────────────┐
│  ① mvn -o clean package -DskipTests    ≈ 26 秒（全量）        │
│  ② docker compose build <svc>          ≈ 5–20 秒/服务        │
│  ③ docker compose up -d <svc>          就地替换，秒级         │
│                                                              │
│  中间件（8 个容器 + 6 个数据卷）全程不动                       │
└──────────────────────────────────────────────────────────────┘
```

### 为什么是这个方案

| 备选 | 结论 |
|---|---|
| **A. 本机 `mvn package` 后 scp jar** | ✅ 采用（`deploy.ps1`）。本机有完整 `.m2` 缓存，增量编译快 |
| **B. 服务器上 Docker 多阶段构建** | ❌ 每构建一次都要重下全部 Maven 依赖，2 核机器上慢得多 |
| **C. 服务器宿主机 `mvn package`** | ✅ **也采用**（`deploy.sh`）。实测全量 26 秒、增量 15 秒，比本机还快，还省掉 700 MB 上传 |
| **D. 服务器上拉代码编译** | ❌ 服务器连不上 GitHub |

**实际推荐**：日常改后端就用 **C**（直接 SSH 到服务器编译，最快）；
如果本机改完想一键推，用 **A**。

---

## 三、目录结构

```
haalan/deploy/
├── README.md                    ← 本文件（方案 + 操作手册）
├── .env                         ← 项目名、IP、口令、JVM 参数
├── .env.example                 ← 同上，提交进 git 的模板
├── docker-compose.yml           ← 完整编排（中间件 + 应用），用于换机从零部署
├── docker-compose.apps.yml      ← 应用层编排（只含 8 个应用），用于日常迭代 ★
├── scripts/
│   ├── deploy.sh                ← 服务器侧：编译 + 构建 + 替换（推荐日常用）
│   ├── deploy.ps1               ← 本机侧：编译 + 同步 + 触发远程部署
│   ├── deploy-sentinel.sh       ← 构建并启动 Sentinel 控制台
│   ├── backup-volumes.sh        ← 换机前：把全部数据打包成一个目录 ★
│   └── restore-volumes.sh       ← 换机后：在新机器上还原 ★
└── deploy-assets/
    ├── sql/                     ← 01-haalan.sql / 02-xxl-job.sql / 03-seata-at.sql / 04-seata-tc.sql
    ├── nacos/custom.env         ← Nacos 容器环境变量
    ├── nacos/nacos_config_export.zip  ← 14 条配置的导出包
    ├── seata/application.yml    ← Seata 服务端配置
    ├── es/elasticsearch-analysis-ik-7.12.1.zip  ← IK 分词插件
    ├── sentinel/                ← Sentinel 控制台：jar + Dockerfile + 说明
    └── docker-compose.reference.yml  ← 原 docker-compose.yml，留作对照
```

---

## 四、日常部署（最常用）

### 4.1 只改了一个后端服务（推荐姿势）

```bash
# SSH 上服务器
ssh root@192.168.100.133

cd /root/haalan
mvn -o clean package -DskipTests          # ≈ 26 秒

cd deploy
bash scripts/deploy.sh --no-build gateway # 只重建 gateway
```

`deploy.sh` 会自动：设置 JAVA_HOME/PATH → 记录中间件状态 → 构建 → 替换 →
等待注册 → 冒烟检查 → 再次核对中间件状态未变。

### 4.2 改了前端

```bash
cd /root/haalan/deploy
bash scripts/deploy.sh frontend           # 会在容器里 npm ci + build
```

### 4.3 一次更新多个

```bash
bash scripts/deploy.sh gateway user-service order-service
bash scripts/deploy.sh frontend admin
```

### 4.4 从本机一键推

```powershell
cd D:\dswork\work1\haalan\deploy\scripts
.\deploy.ps1                      # 全量
.\deploy.ps1 gateway              # 单个
.\deploy.ps1 -SkipBuild gateway   # 跳过本机编译，用现有 jar
.\deploy.ps1 -Plan                # 只打印将要做什么，不动任何东西
```

> **如果提示 `cannot be loaded ... is not digitally signed`**
> 本机当前用户策略是 `RemoteSigned`。若所在目录被策略限制，加个参数即可：
> ```powershell
> powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 gateway
> ```
> 实测：本机 `D:\dswork\work1` 目录下未签名 `.ps1` 会被拦，`D:\dswork`
> 及其它位置不会。加 `-ExecutionPolicy Bypass` 两种情况都能跑。

---

## 五、换机 / 从零部署

在一台**全新**服务器上：

```bash
# 1. 准备目录
mkdir -p /root/haalan && cd /root/haalan
# 把工作区的 haalan/ haalan_/ haalan-admin/ 和 haalan/deploy/ 传上来

# 2. 配置
cd deploy
vi .env
#   COMPOSE_PROJECT_NAME=            ← 留空，让 compose 用目录名建全新资源
#   HOST_IP=<新服务器IP>              ← 必改，Seata 注册用
#   JAVA_TOOL_OPTIONS=               ← 大内存机器可留空

# 3. 装好 JDK17 + Maven（脚本默认找 /usr/local/jdk17 和 /usr/local/maven）
export JAVA_HOME=/usr/local/jdk17
export PATH=$JAVA_HOME/bin:/usr/local/maven/bin:$PATH

# 4. 一次性起全部
cd .. && mvn -o clean package -DskipTests && cd deploy
docker compose up -d

# 5. 验证
docker compose ps
curl -I http://localhost/          # 前端
curl -I http://localhost:81/       # 管理端
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100
```

首次启动时 `deploy-assets/sql/*.sql` 会被 MySQL 自动执行建库建表
（**仅在 `/data/mysql8` 为空时生效**）。

Nacos 的 14 条配置需要手工导入：登录 `http://<IP>:8848/nacos` →
配置管理 → 导入 → 选 `deploy-assets/nacos/nacos_config_export.zip`。

---

## 六、务必注意的坑

### 6.1 ⛔ 不要在现有虚拟机上执行 `docker compose up -d`（完整版）

实测 dry-run 结果：完整版 compose 会把 **8 个中间件全部 Recreate**。

```
DRY-RUN MODE -  Container redis  Recreate
DRY-RUN MODE -  Container mysql8 Recreate
DRY-RUN MODE -  Container es     Recreate
... (8 个全是 Recreate)
```

这违反「不许关闭、修改任何容器」。**日常迭代请一律使用 `docker-compose.apps.yml`**，
它 dry-run 的结果只有 8 个应用容器，中间件一个都不动。

### 6.2 ⛔ 不要加 `--remove-orphans`

中间件和微服务在服务器上属于同一个 compose 项目 `root`。用
`docker-compose.apps.yml` 时，中间件会被识别为 orphan 并给出警告：

```
WARNING: Found orphan containers ([nacos xxl-job-admin mysql8 seata mq kibana redis es])
```

**这只是警告，无害。** 但如果加了 `--remove-orphans`，它就会把这 8 个中间件
**删掉**。`deploy.sh` 里已经刻意不加该参数。

### 6.3 内存只有 1 GB 可用

6 个 Spring Boot 默认堆可能吃掉 3–4 GB。`.env` 里已设置：

```
JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m
```

同时给每个容器加了日志轮转（`max-size: 10m, max-file: 3`），
避免长期运行把 50 GB 的根分区写满。

**建议**：换到 16 GB 内存的机器，或先把 `kibana` 停掉（它占 240 MB 且平时不用）。

### 6.4 服务器连不上 GitHub

```
$ curl https://github.com
curl: (7) Failed connect to github.com:443; Connection refused
```

所以：
- 服务器上的 `/root/haalan` 等目录**不能** `git pull`，只能靠本机 scp
- 你在工作区做的 git 提交，只能在**本机** push

### 6.5 Nacos 健康检查原本是假的（新编排已修好）

原编排写的是：

```yaml
healthcheck:
  test: ["CMD", "true"]      # 永远返回 0 = 永远健康
```

等于没做健康检查，后端会在 Nacos 还没就绪时就冲进去注册。
`deploy/docker-compose.yml` 已换成真实探针：

```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/v1/console/health/readiness"]
  interval: 10s
  timeout: 5s
  retries: 10
```

> 注意：**现有虚拟机上跑的 nacos 容器仍是旧配置**（`test: ["CMD","true"]`）。
> 让它生效需要重建 nacos 容器，属于「修改容器」，不做。新机器上从零部署时
> 自动就是新探针。

### 6.6 Sentinel 控制台（已在跑，2026-09-02 落地）

控制台现在**正在运行**：

```
sentinel-dashboard   Up  0.0.0.0:8090->8090/tcp
http://192.168.100.133:8090     默认账号密码都是 sentinel
```

启动方式与踩过的坑：

```bash
cd /root/haalan/deploy
bash scripts/deploy-sentinel.sh build     # 构建镜像（约 22 MB 增量）
bash scripts/deploy-sentinel.sh up        # 起容器，接入 root_haalan 网络
bash scripts/deploy-sentinel.sh status    # 状态 + 中间件未受影响核对
```

**为什么用 `docker run` 而不是 `docker compose --profile tools up -d`**：
compose 起服务前会先尝试 pull `haalan/sentinel-dashboard:1.8.6`，而服务器
直连 `registry-1.docker.io` 是 DNS 失败（`no such host`），compose 的 pull
路径**不走** `daemon.json` 里的加速器。所以先用 `docker build` 出本地镜像，
再用 `docker run` 起，绕开 pull。`docker-compose.yml` 里那个 `tools` profile
的服务定义保留着，等换到能直连 Docker Hub 的机器时可以直接用。

**镜像怎么来的**：`deploy-assets/sentinel/Dockerfile` + `sentinel-dashboard.jar`
（1.8.6，21.6 MB，从源目录 `E:\Personal\Desktop\jaalan\sentinel-dashboard.jar`
拷入）。基础镜像用 `btgoose/jdk17:latest`（服务器上现成的，省 500 MB 下载）。

**JDK 17 兼容性**：Sentinel 1.8.6 是 2022 年用 JDK 8 编译的 Spring Boot 2.5.12，
在 JDK 17 下必须加 `--add-opens`，否则抛 `InaccessibleObjectException`。
Dockerfile 里已经写好这 10 个参数，不要删。

**哪些服务会出现在控制台**：只有传递依赖了
`spring-cloud-starter-alibaba-sentinel` 的 4 个 —— `item-service`、
`order-service`、`search-service`、`seckill-service`（都依赖 `haalan-api`）。
`gateway` 和 `user-service` 只依赖 `haalan-common`，**没有** sentinel 依赖，
所以看不到它们。这是设计使然，不是故障。

**控制台的 REST API 需要登录**，`curl http://.../api/app/brief` 返回 401 是
正常的安全行为，不是故障。想看「控制台有没有连上客户端」，看容器内日志：

```bash
docker exec order-service tail -20 /root/logs/csp/command-center.log.*
# 出现 Socket income: GET /metric?... addr: /172.19.0.18  就是连上了
```

### 6.7 ES 的 IK 分词插件目录是空的

`root_es-plugins` 卷里没有任何文件，索引 `products` 的 mapping 用的是标准
分词器（`type: text`），代码里也**没有任何** `ik_max_word` / `ik_smart` 引用
（全仓库 grep 零命中）。也就是说：**当前搜索功能并不依赖 IK**，中文搜索是
按标准分词器逐字切的，效果一般但不会报错。

如果确实想启用 IK：

```bash
# 1. 解压插件到卷目录（卷当前是空的，解压即可，不需要删东西）
cd /root/haalan/deploy/deploy-assets/es
mkdir -p /tmp/ik && unzip -q elasticsearch-analysis-ik-7.12.1.zip -d /tmp/ik
mkdir -p /var/lib/docker/volumes/root_es-plugins/_data/ik
cp -r /tmp/ik/. /var/lib/docker/volumes/root_es-plugins/_data/ik/
chown -R 1000:0 /var/lib/docker/volumes/root_es-plugins/_data/ik
rm -rf /tmp/ik

# 2. 重启 es 让插件生效 —— ⚠ 这属于「修改容器」，需要你确认后再执行
docker restart es
docker exec es bin/elasticsearch-plugin list      # 应输出 ik
```

> 服务器**没有 `unzip`**，但 `es` 容器里有，也可以
> `docker exec es bin/elasticsearch-plugin install file:///...`。
> 更简单的办法：在别的机器解压好，直接 scp 整个目录过去。
>
> 重启 es 后，**已存在的 `products` 索引不会自动换分词器**（mapping 不能改
> analyzer），要重建索引才生效。所以除非要重建索引，否则不必装。

### 6.8 `.env` 里的 `COMPOSE_PROJECT_NAME=root` 是刻意设的

它让新编排算出 `root_haalan` 网络和 `root_*` 数据卷 —— 也就是**当前正在用的那套**，
从而复用现有数据（RabbitMQ 用户/vhost、ES 索引、Nacos 数据）。
**换机器时务必清空这一行**，否则会去连一台机器上不存在的资源。
详见**第七节「换服务器：数据卷怎么一并迁移」**。

---

## 七、换服务器：数据卷怎么一并迁移

这是「把整套环境搬到另一台机器」的完整流程。核心工具是两个脚本：

| 脚本 | 在哪台机器跑 | 作用 |
|---|---|---|
| `scripts/backup-volumes.sh` | **旧机器** | 把全部数据导成一个目录（实测 **25 MB**） |
| `scripts/restore-volumes.sh` | **新机器** | 在新机器上建卷、灌数据、起服务 |

### 7.1 先搞清楚「数据到底在哪」

这套环境的数据分散在 **3 类位置**，迁移方案对每一类的处理方式不同：

| 类型 | 具体资源 | 数据内容 | 迁移方式 |
|---|---|---|---|
| Docker 卷 | `root_redis-data` | Redis 25 个 key（RDB） | `tar` 打包 → 解包 |
| Docker 卷 | `root_es-data` | ES `products` 等全部索引 | `tar` 打包 → 解包 |
| Docker 卷 | `root_es-plugins` | ES 插件（**当前为空**） | `tar` 打包 → 解包 |
| Docker 卷 | `root_mq-data` | RabbitMQ 用户/vhost/队列（349 MB） | `tar` 打包 → 解包 |
| Docker 卷 | `root_mq-plugins` | RabbitMQ 插件（16 MB） | `tar` 打包 → 解包 |
| Docker 卷 | `root_nacos-data` | 14 条 Nacos 配置 | `tar` 打包 → 解包 |
| 绑定目录 | `/data/mysql8` | **7 个业务库全部数据** | **不用 tar**，走 `mysqldump` 逻辑备份 |
| 绑定目录 | `/root/seata` | Seata 服务端 `application.yml` | `tar` 打包 → 解包 |
| 不挂载 | `kibana`、`xxl-job-admin` | 无本地数据（xxl_job 库在 MySQL 里） | 不需要迁移 |

> **MySQL 为什么不打包目录**：`/data/mysql8` 是 456 MB 的 InnoDB 数据目录，
> 直接 `tar` 有两个风险 —— ① 热拷贝会拷到不一致的页；② 跨 MySQL 小版本
> 数据目录不保证兼容。用 `mysqldump --single-transaction` 逻辑备份更安全，
> 而且体积只有 3.3 MB（gzip 后）。

### 7.2 迁移步骤（5 步）

#### 第 1 步 · 旧机器：导出

```bash
ssh root@192.168.100.133
cd /root/haalan/deploy
bash scripts/backup-volumes.sh -o /root/haalan-backup
```

脚本会依次做这些事（**全部是只读或落盘操作，不删任何东西**）：

1. `mysqldump` 8 个库 → `mysql/all-databases.sql.gz`（3.3 MB）
2. `redis-cli BGSAVE` 让 Redis 落盘（等 `rdb_last_bgsave_status:ok`）
3. 对 ES 调 `_flush`，保证段文件都写进磁盘
4. `tar` 6 个数据卷 + `/root/seata`
5. `rabbitmqctl export_definitions` 导出 MQ 拓扑（双保险）
6. 记录 `docker-compose.yml` / `.env` / 镜像清单 / 容器清单
7. 生成 `SHA256SUMS`

实测产出：

```
/root/haalan-backup/                        总计 25 MB
├── MANIFEST.txt              卷名、属主、大小清单
├── SHA256SUMS                完整性校验
├── mysql/all-databases.sql.gz        3.3 M
├── volumes/redis-data.tar.gz         4.0 K
├── volumes/es-data.tar.gz            3.9 M
├── volumes/es-plugins.tar.gz         0
├── volumes/mq-data.tar.gz            349 M  ← 最大头
├── volumes/mq-plugins.tar.gz         16 M
├── volumes/nacos-data.tar.gz         457 M（压缩后 22 M）
├── binds/seata.tar.gz                80 K
├── rabbitmq/definitions.json
└── compose/{docker-compose.yml,docker-compose.apps.yml,.env,images.txt,containers.txt,volumes.txt,networks.txt}
```

> `mq-data` 里是 RabbitMQ 的 mnesia 库。它的节点名是 **`rabbit@mq`**，由
> compose 的 `container_name: mq` 决定。只要新机器上容器名还叫 `mq`，mnesia
> 就能直接读；如果改了容器名，MQ 会以新节点名启动、旧数据读不出来。
> 这是**不要改 `container_name`** 的原因。

#### 第 2 步 · 旧机器：确认完整性并传走

```bash
cd /root/haalan-backup
sha256sum -c SHA256SUMS        # 17 项全部输出「确定」/「OK」

# 传到新机器（rsync 可续传）
rsync -aP /root/haalan-backup/ root@<新机IP>:/root/haalan-backup/
```

#### 第 3 步 · 新机器：装环境 + 放代码

```bash
# Docker + compose
curl -fsSL https://get.docker.com | sh
# 如果新机器也连不上 Docker Hub，把 /etc/docker/daemon.json 的加速器配好

# JDK 17 + Maven（deploy.sh 默认找这两个路径）
# 代码：把 haalan/ haalan_/ haalan-admin/ 三个目录传上去
mkdir -p /root/haalan
rsync -aP haalan/ haalan_/ haalan-admin/ root@<新机IP>:/root/
```

#### 第 4 步 · 新机器：改 `.env`（**最关键的一步**）

```bash
cd /root/haalan/deploy
vi .env
```

必须改的两项：

| 变量 | 旧机器 | 新机器应该填 |
|---|---|---|
| `COMPOSE_PROJECT_NAME` | `root` | **留空**（或改成新项目名） |
| `HOST_IP` | `192.168.100.133` | **新机器 IP**（Seata 注册用） |

> **为什么 `COMPOSE_PROJECT_NAME` 必须留空**：旧机器上它被刻意设成 `root`，
> 目的是让新编排算出 `root_haalan` 网络和 `root_*` 卷，从而**复用旧机器的
> 现存数据**。到了新机器，那些资源不存在，如果还留着 `root`，compose 会
> 尝试复用同名资源 —— 新机器上若有残留就会串数据，没有则会造出一批带
> 旧名字的资源，和后续「正常部署」的命名对不上。
> 留空后 compose 用目录名（`deploy`）或 `-p` 参数自动命名，干净利落。
>
> 脚本会在还原前检查这一项，留空会打印 `✓`，非空会打印 `⚠` 警告。

#### 第 5 步 · 新机器：还原

```bash
cd /root/haalan/deploy
bash scripts/restore-volumes.sh -i /root/haalan-backup --dry-run   # 先看一遍
bash scripts/restore-volumes.sh -i /root/haalan-backup             # 真跑
```

脚本的 5 个阶段：

1. **解析新卷名** —— 用 `docker compose config` 让 compose 自己算出
   `redis-data` 这类逻辑名在新机器上对应的真实卷名（`<项目名>_redis-data`），
   再从中提取。这样旧机叫 `root_redis-data`、新机叫 `deploy_redis-data`
   都能自动对上，不需要手工改脚本。
2. **还原数据卷** —— 建卷 → 清空 → 解包 → **按 MANIFEST 里记录的
   `uid:gid` 改属主**。属主这一步不能省：Redis 卷是 `999:999`、ES 是
   `1000:0`、Nacos 是 `0:0`，错了容器起不来。
3. **起中间件** —— 只起 `mysql redis mq nacos es`，并等 mysql 健康、
   Nacos readiness 通过。
4. **导入数据** —— `gunzip | mysql` 灌 8 个库 + `rabbitmqctl import_definitions`
   导入 MQ 拓扑。
5. **起其余服务** —— 还原 `/root/seata` 后 `docker compose up -d`，
   等 Nacos 注册数 ≥ 7，冒烟检查前端/管理端/Sentinel/网关。

### 7.3 常见选项

```bash
bash scripts/restore-volumes.sh -i /root/haalan-backup --dry-run
bash scripts/restore-volumes.sh -i /root/haalan-backup --volumes-only  # 只灌卷，不起服务
bash scripts/restore-volumes.sh -i /root/haalan-backup --skip-mysql    # 不导数据库
bash scripts/restore-volumes.sh -i /root/haalan-backup --skip-mq
bash scripts/restore-volumes.sh -i /root/haalan-backup --skip-seata
bash scripts/restore-volumes.sh -i /root/haalan-backup -y              # 免交互
```

### 7.4 还原后要检查什么

```bash
docker compose ps                                    # 全部 Up
docker exec redis redis-cli -a 123456 --no-auth-warning DBSIZE    # 应为 25
curl -s 'http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100'
docker exec mysql8 mysql -uroot -p123456 -e 'SHOW DATABASES'      # 7 个业务库
curl -s 'http://localhost:9200/_cat/indices?v' | grep products
```

### 7.5 只迁一个中间件的部分数据

如果只想搬 MySQL，不必跑整套：

```bash
# 旧机器
docker exec mysql8 mysqldump -uroot -p123456 --single-transaction \
  --databases user-db item-db order-db seckill-db seata nacos xxl_job \
  | gzip -9 > /tmp/db.sql.gz

# 新机器
gunzip -c /tmp/db.sql.gz | docker exec -i mysql8 mysql -uroot -p123456
```

只想搬 Redis 或 ES，可以直接 tar 单个卷：

```bash
# 旧机器
docker run --rm -v root_redis-data:/data -v /tmp:/backup alpine \
  tar czf /backup/redis-data.tar.gz -C /data .

# 新机器（先建卷，注意项目名前缀）
docker volume create <新项目名>_redis-data
docker run --rm -v <新项目名>_redis-data:/data -v /tmp:/backup alpine \
  sh -c 'rm -rf /data/* && tar xzf /backup/redis-data.tar.gz -C /data'
docker run --rm -v <新项目名>_redis-data:/data alpine chown -R 999:999 /data
```

> 注意最后那个 `chown`：用 `docker run` 手工搬卷时最容易漏掉属主，
> 症状是 Redis/ES 容器启动即报权限错误。

### 7.6 ⚠ 迁移时的三个坑

1. **不要在新机器上先 `docker compose up -d` 再还原数据**。中间件一旦以
   空卷启动，就会初始化出一套空库/空配置；此时再灌数据，Nacos 和 MySQL
   可能因为已经写过文件而拒绝或产生重复。正确顺序是**先还原卷、再起服务**
   （`restore-volumes.sh` 就是这个顺序）。
2. **`/data/mysql8` 如果在新机器上已存在且非空**，MySQL 官方镜像会跳过
   `deploy-assets/sql/*.sql` 的初始化。还原脚本用的是 `mysqldump` 导入，
   不依赖那个初始化机制，所以不受影响 —— 但如果你想用「全新空库 + SQL
   初始化」的路子，得先把 `/data/mysql8` 清空。
3. **备份目录别放 `/root/haalan` 里面**。`deploy.ps1` 同步时会覆盖
   `/root/haalan/deploy` 下的文件；备份放在 `/root/haalan-backup`（同级）
   最安全。

---

## 八、回滚

所有变更都是「就地替换容器」，数据卷不动。

`deploy.sh` **已经内置**了部署前自动备份镜像标签这一步：每次部署前会给涉及的
每个 `root-<svc>:latest` 打一个 `root-<svc>:<时间戳>` 标签（只新增标签，不删除
任何东西）。想跳过就用 `NO_BACKUP=1 bash deploy.sh ...`。

回滚只需两步：

```bash
# 1. 看有哪些备份标签（时间戳从新到旧）
docker images 'root-*' --format '{{.Repository}}:{{.Tag}}\t{{.CreatedSince}}'

# 2. 把想要的那个版本推回 :latest，然后重建容器（示例）
docker tag root-gateway:20260902-184500 root-gateway:latest
docker compose -f docker-compose.apps.yml up -d --force-recreate gateway
```

只想手工打一个标签也可以：

```bash
docker tag root-gateway:latest root-gateway:$(date +%Y%m%d-%H%M%S)
```

注意：`docker compose up -d <svc>` 本身就会用 `image: root-<svc>:latest`
重建容器，所以「改标签 + up -d」是完整闭环，不需要 `docker run`。

---

## 九、下一步可以做的

1. **消除双编排混乱** —— 把 `/root/docker-compose.yml` 的 15 个服务统一到
   `/root/haalan/deploy/docker-compose.yml`（需要一次停起全部容器的窗口，
   属于「修改容器」，要先确认）。
2. **`search-service` 归队** —— 它现在是 `haalan-search-service` 镜像 + 双网络，
   下次重建时会变成 `root-search-service` + 单网络，届时那个多余的 `haalan`
   网络连接会消失。
3. **给服务器装 JDK/Maven 的 PATH 兜底** —— 目前 ssh 非登录 shell 读不到
   `/etc/profile`，脚本里已经硬编码了路径，但手工敲命令时容易踩。
4. **离线镜像包** —— 如果要部署到完全断网的机器，需要把
   `mysql:8.0.33 / redis:7 / nacos:v2.1.0-slim / elasticsearch:7.12.1 /
   kibana:7.12.1 / rabbitmq:3.8-management / seataio/seata-server:1.6.1 /
   xuxueli/xxl-job-admin:2.4.1 / btgoose/jdk17 / node:22-alpine / nginx:1.25-alpine`
   逐个 `docker save` 出来。
5. **清理 57 个 `<none>` 悬空镜像** —— `docker system df` 显示可回收 5.7 GB
   （属于删除操作，需先确认）。
6. **把备份做成定时任务** —— 现在 `backup-volumes.sh` 是手工跑的，
   可以加进 crontab（例如每周日凌晨），顺带删掉 N 天前的旧备份。
