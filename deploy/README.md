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

### 1.3 历史上存在两套 compose 编排（2026-09-02 已统一）

改造**之前**服务器上有两个 compose 项目：

| compose 项目 | 配置文件 | 包含的服务 |
|---|---|---|
| `root` | `/root/docker-compose.yml` | **15 个**：8 个中间件 + 6 个微服务 + 2 个前端 |
| `haalan` | `/root/haalan/docker-compose.yml` | 7 个：6 微服务 + frontend |

各容器的归属（改造前）：

```
root 项目    → mysql8 redis nacos es kibana mq seata xxl-job-admin
              gateway user-service item-service order-service
              seckill-service frontend haalan-admin
haalan 项目  → search-service        ← 唯一一个从 /root/haalan 起起来的
```

**`search-service` 当时是个特例**：镜像是 `haalan-search-service`（构建于
2026-05-31），而不是像其他 5 个后端那样的 `root-*`（构建于 2026-07-30）；
它还**同时接在两个网络上**（`haalan` 和 `root_haalan`）—— 这是历史上
排查它连不上 Nacos 时手工 `docker network connect` 留下的痕迹。

> ✅ **已于 2026-09-02 归队**：现在是 `root` 项目的
> `root-search-service:latest`，单网络 `root_haalan`（172.19.0.13）。
> 旧镜像 `haalan-search-service:latest` 和旧容器参数
> （`/root/search-service-old-inspect.json`）都保留着，需要时可回退。

统一之后 `docker compose ls -a` 只剩一个项目：

```
NAME   STATUS         CONFIG FILES
root   running(16)    /root/haalan/deploy/docker-compose.apps.yml,/root/docker-compose.yml
```

> 归队后各容器的 `com.docker.compose.project.config_files` 标签是这样的：
> 8 个中间件 + `gateway`/`user-service`/`frontend`/`haalan-admin` 仍标着
> `/root/docker-compose.yml`（它们是旧文件创建的），而
> `item-service`/`order-service`/`search-service`/`seckill-service` 标着
> `/root/haalan/deploy/docker-compose.apps.yml`。**两者都是 `project=root`**，
> 所以互相认得，不是孤儿。**不要删 `/root/docker-compose.yml`** ——
> 删了之后那 12 个容器下次操作时会被当成孤儿。

### 1.4 数据卷与网络（必须复用，不能重建）

| 资源 | 实际名字 | 挂载到 |
|---|---|---|
| 网络 | `root_haalan` | 全部 17 个容器（含 sentinel-dashboard） |
| 卷 | `root_redis-data` | redis `/data` |
| 卷 | `root_es-data` | es `/usr/share/elasticsearch/data` |
| 卷 | `root_es-plugins` | es plugins（**已装 `analysis-ik` 7.12.1**，见 6.7） |
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
├── skill/SKILL.md               ← 部署运维 skill（可给 AI 助手直接加载，内容与本文同源）
├── .env                         ← 项目名、IP、口令、JVM 参数
├── .env.example                 ← 同上，提交进 git 的模板
├── docker-compose.yml           ← 完整编排（中间件 + 应用），用于换机从零部署
├── docker-compose.apps.yml      ← 应用层编排（只含 8 个应用），用于日常迭代 ★
├── scripts/
│   ├── deploy.sh                ← 服务器侧：编译 + 构建 + 替换（推荐日常用）
│   ├── sync-source.ps1          ← 本机侧：把源码打成 170 KB 包同步到服务器 ★
│   ├── deploy.ps1               ← 本机侧：本机编译 + 上传 jar（约 700 MB）
│   ├── deploy-sentinel.sh       ← 构建并启动 Sentinel 控制台
│   ├── backup-volumes.sh        ← 换机前：把全部数据打包成一个目录 ★
│   ├── restore-volumes.sh       ← 换机后：在新机器上还原 ★
│   ├── cron-backup.sh           ← 定时备份（crontab 入口 + 按天轮转）
│   ├── pack-images.sh           ← 断网机器：把全部基础镜像 docker save 出来
│   └── reindex-products.sh      ← 把 products 索引换成带 IK 分词器的版本
└── deploy-assets/
    ├── sql/                     ← 01-haalan.sql / 02-xxl-job.sql / 03-seata-at.sql / 04-seata-tc.sql
    ├── nacos/custom.env         ← Nacos 容器环境变量
    ├── nacos/nacos_config_export.zip  ← 14 条配置的导出包
    ├── seata/application.yml    ← Seata 服务端配置
    ├── es/elasticsearch-analysis-ik-7.12.1.zip  ← IK 分词插件
    ├── es/products-mapping.json ← products 索引的 IK mapping（建索引用）
    ├── sentinel/                ← Sentinel 控制台：jar + Dockerfile + 说明
    └── docker-compose.reference.yml  ← 原 docker-compose.yml，留作对照
```

本机另有两个快捷命令（不在这个目录里，见 4.0 节）：

| 位置 | 内容 |
|---|---|
| `C:\Users\Haaland\.local\bin\hs.ps1` / `hs.cmd` | `hs` —— SSH 上服务器 |
| `C:\Users\Haaland\.local\bin\hd.ps1` / `hd.cmd` | `hd` —— 同步源码 + 编译 + 重建应用 |
| `C:\Users\Haaland\.ssh\config` | `Host haalan` / `Host 133` 别名 |

---

## 四、日常部署（最常用）

### 4.0 快捷命令 `hs` / `hd`（本机已装好，敲两个字母）

不用再敲 `ssh root@192.168.100.133`。本机已配置**密钥免密登录**并装了两个包装脚本：

| 命令 | 作用 | 例子 |
|---|---|---|
| `hs` | SSH 上服务器 | `hs` 进交互式；`hs docker ps` 直接跑一条命令 |
| `hd` | 在服务器上编译 + 重建应用容器 | `hd gateway`；`hd` 全量；`hd -Status` 只看状态 |

```powershell
hs                                  # 交互式登录
hs docker ps                        # 跑一条命令就回来
hs "docker logs --tail 50 gateway"  # 带空格的命令加引号

hd                                  # 同步源码 + 全量重建 8 个应用
hd gateway                          # 同步源码 + 只重建 gateway
hd gateway admin                    # 重建指定的几个
hd -NoSync gateway                  # 跳过源码同步，用服务器现有源码重建
hd -NoBuild gateway                 # 跳过 mvn，用现有 jar 重建镜像
hd -SyncOnly                        # 只同步源码，不编译不部署
hd -Plan gateway                    # 只打印将要做什么，不动任何东西
hd -List                            # 列出可用服务名
hd -Status                          # 只看容器状态，不部署
```

**⚠️ 为什么 `hd` 默认会先同步源码（这是最容易踩的坑）**

`deploy.sh` 是在**服务器上**跑 `mvn`，编译的是 `/root/haalan` 里的源码。

所以如果你在本机改完代码、直接跑 `hd gateway`，而源码没同步过去 —— 服务器编译的还是旧代码，**部署了等于没部署**，容器重建了但行为没变，很容易误判成"改动没生效"。

`hd` 因此把「同步源码」作为默认第一步：

```
本机源码打包（约 170 KB）
   → scp 上传 /tmp/haalan-src.tar.gz
   → 服务器上 tar -xzf --overwrite 解包到 /root/haalan
   → cd /root/haalan/deploy && bash scripts/deploy.sh <服务名>
```

实测：本机 8 个模块源码 353 个文件、打包 173.7 KB、上传+解包后与服务器 **353/353 文件 MD5 完全一致**。

打包只收 `pom.xml` 与各模块的 `src/`、`pom.xml`，**不含** `target/`、`.git/`、`.idea/`、`logs/`；解包用 `--overwrite` 只覆盖同名文件，**不删除**服务器上任何东西。

**装在哪、怎么来的**（换电脑时照做一遍）：

- 密钥：本机 `C:\Users\Haaland\.ssh\id_ed25519.pub` 已追加到服务器 `/root/.ssh/authorized_keys`
- 别名：`C:\Users\Haaland\.ssh\config` 里定义了 `Host haalan` 和 `Host 133`，所以 `ssh haalan` 也能直接连
- 脚本：`C:\Users\Haaland\.local\bin\hs.ps1`、`hd.ps1`（另有同名 `.cmd` 供 cmd.exe 用）
  `C:\Users\Haaland\.local\bin` 已在本机 PATH 里，所以**新开终端**就能直接用
- 同步脚本：`D:\dswork\work1\haalan\deploy\scripts\sync-source.ps1`（`hd` 内部调用它，也可单独用）

> `hd` **只操作 8 个应用容器**，绝不碰中间件。

### 4.0.1 三条路的对比（选一条就行）

| 方式 | 命令 | 编译在哪 | 传输量 | 耗时 | 适用 |
|---|---|---|---|---|---|
| **服务器编译**（推荐） | `hd gateway` | 服务器 | 源码 ~170 KB | 编译 ≈26 秒 | 日常改后端 |
| 本机编译 | `.\deploy.ps1 gateway` | 本机 | jar ~700 MB | 本机 mvn ≈2 分钟 | 本机想先跑测试 |
| 纯手工 | `hs` 进去敲 `deploy.sh` | 服务器 | 0（需自己先同步源码） | ≈26 秒 | 调试脚本时 |

> 手工方式务必记得先把源码弄上去，否则同样会踩「编译旧代码」的坑。

### 4.1 只改了一个后端服务（推荐姿势）

假设你在本机改了 `user-service` 的代码：

```powershell
hd user-service
```

就这一条。它内部依次做四件事：

```
1/4 打包本机源码（8 个模块的 src + pom.xml，约 170 KB）
2/4 scp 上传到 /tmp/haalan-src.tar.gz
3/4 服务器 tar -xzf --overwrite 解包到 /root/haalan（只覆盖，不删）
4/4 cd /root/haalan/deploy && bash scripts/deploy.sh user-service
      └─ 服务器 mvn -o clean package -DskipTests   （≈26 秒）
      └─ docker compose build user-service
      └─ docker compose up -d user-service          （只重建这一个容器）
      └─ 等 Nacos 注册 + 冒烟检查 + 核对中间件未受影响
```

耗时约 1 分钟。**中间件一个都不会动**（deploy.sh 用的是 `docker-compose.apps.yml`，
里面只有 8 个应用）。

不想直接部署、想先看看它会做什么：

```powershell
hd -Plan user-service      # 只打印计划，不动任何东西
hd -SyncOnly               # 只同步源码，稍后再部署
hd -Status                 # 只看容器状态
```

**纯手工方式**（不用 `hd`，调试脚本时用）：

```bash
hs                                    # SSH 上服务器（= ssh haalan）
cd /root/haalan
mvn -o clean package -DskipTests      # ≈ 26 秒
cd deploy
bash scripts/deploy.sh --no-build user-service
```

> ⚠️ 手工方式**务必先同步源码**（`.\sync-source.ps1 -SyncOnly`），
> 否则服务器编译的还是旧代码 —— 见 6.9 节。

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

### 4.4 从本机一键推（本机编译 + 上传 jar）

```powershell
cd D:\dswork\work1\haalan\deploy\scripts
.\deploy.ps1                      # 全量
.\deploy.ps1 gateway              # 单个
.\deploy.ps1 -SkipBuild gateway   # 跳过本机编译，用现有 jar
.\deploy.ps1 -Plan                # 只打印将要做什么，不动任何东西
```

> ⚠️ 这个脚本**只上传 jar，不上传源码**。所以它编译的是**本机**的代码（本机 mvn），
> 结果是对的；但如果你之后改用服务器编译（`hd` / `deploy.sh`），服务器上的源码
> 可能还是旧的 —— 那种情况下先跑 `.\sync-source.ps1` 或直接用 `hd`。

> **如果提示 `cannot be loaded ... is not digitally signed`**
> 本机当前用户策略是 `RemoteSigned`。若所在目录被策略限制，加个参数即可：
> ```powershell
> powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 gateway
> ```
> 实测：本机 `D:\dswork\work1` 目录下未签名 `.ps1` 会被拦，`D:\dswork`
> 及其它位置不会。加 `-ExecutionPolicy Bypass` 两种情况都能跑。

### 4.5 只同步源码，不部署

改了代码但想稍后再部署，或想先确认源码已同步：

```powershell
cd D:\dswork\work1\haalan\deploy\scripts
.\sync-source.ps1 -SyncOnly       # 只打包 + 上传 + 解包，不编译不部署
.\sync-source.ps1 -Plan           # 只打印将要做什么
.\sync-source.ps1 -NoSync gateway # 跳过同步，只用服务器现有源码重建
```

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

### 5.4 断网机器：先把基础镜像导过去

如果新机器**完全连不上网**（连 Docker Hub 加速器也不通），
先把 12 个基础镜像打包：

```bash
# 在能上网的机器上（就是现在这台）
bash scripts/pack-images.sh -o /root/haalan-images
# 产出：12 个 .tar.gz（共 2.3 GB）+ load-images.sh + MANIFEST.txt + SHA256SUMS

sha256sum -c /root/haalan-images/SHA256SUMS        # 校验
rsync -aP /root/haalan-images/ root@<新机IP>:/root/haalan-images/

# 在新机器上
cd /root/haalan-images && bash load-images.sh       # 不带参数 = 全部导入
# 只要某几个：bash load-images.sh mysql:8.0.33 redis:7
```

包含的镜像（实测大小）：

| 镜像 | 大小 |
|---|---|
| mysql:8.0.33 | 155M |
| redis:7 | 43M |
| nacos/nacos-server:v2.1.0-slim | 182M |
| elasticsearch:7.12.1 | 410M |
| kibana:7.12.1 | 389M |
| rabbitmq:3.8-management | 105M |
| seataio/seata-server:1.6.1 | 305M |
| xuxueli/xxl-job-admin:2.4.1 | 106M |
| btgoose/jdk17:latest | 254M |
| node:22-alpine | 57M |
| nginx:1.25-alpine | 19M |
| haalan/sentinel-dashboard:1.8.6 | 273M |

> `pack-images.sh` 管的是**镜像**，`backup-volumes.sh` 管的是**数据**，两者互不替代。
> 应用镜像（`root-*`）不用单独打包 —— 新机器上 `mvn package` + `docker compose build`
> 会重新构建出来，而且那才保证和最新代码一致。

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

### 6.3 内存紧张（可用约 1.5 GB）

实测（2026-09-02，17 个容器全跑）：

```
total 7802 / used 5987 / buff-cache 1636 / available 1515 MB
Swap  8063 / used 1283 / free 6780 MB
```

6 个 Spring Boot 默认堆可能吃掉 3–4 GB。`.env` 里已设置：

```
JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m
```

同时给每个容器加了日志轮转（`max-size: 10m, max-file: 3`），
避免长期运行把 50 GB 的根分区写满。

占内存最大的几个（`docker stats --no-stream`）：

| 容器 | 内存 |
|---|---|
| `nacos` | 656 MB（`JVM_XMS/XMX=1g`） |
| `es` | 616 MB（`-Xms512m -Xmx512m`） |
| `mysql8` | 418 MB |
| `sentinel-dashboard` | 232 MB（已限 `-Xmx256m`） |
| `kibana` | 206 MB |
| `mq` | 68 MB |

**建议**：换到 16 GB 内存的机器；或先停掉 `kibana`（206 MB，平时不用）。
`sentinel-dashboard` 虽然不在原编排里，但 232 MB 是可接受的代价。

### 6.4 服务器连不上 GitHub

```
$ curl https://github.com
curl: (7) Failed connect to github.com:443; Connection refused
```

所以：
- 服务器上的 `/root/haalan` 等目录**不能** `git pull`，只能靠本机 scp
- 你在工作区做的 git 提交，只能在**本机** push

> **本机 push 也需要走代理**。本机直连 `github.com:443` 同样不通
> （`git push` 报 `Failed to connect to github.com port 443 after 21084 ms`），
> 但本机装了代理（`127.0.0.1:7890`，`ProxyEnable=1`）。`git` 不读 IE/WinHTTP
> 代理设置，所以要么每次带参数：
>
> ```bash
> git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push origin master
> ```
>
> 要么一次性写进仓库配置（只影响这个仓库）：
>
> ```bash
> git config http.proxy http://127.0.0.1:7890
> git config https.proxy http://127.0.0.1:7890
> ```
>
> 代理关掉后记得 `git config --unset http.proxy` 去掉，否则会连不上。

#### 顺带一提：非登录 shell 的 PATH（2026-09-02 已修）

`/etc/profile` 里确实有 `JAVA_HOME` / `MAVEN_HOME`，但
**ssh 执行单条命令时不会加载它** —— 于是出现「明明装了 JDK，`java -version`
却报 `java: 未找到命令`」这种反直觉现象。

已经补好两层兜底：

```bash
# 1) /etc/profile.d/java.sh（登录 shell 走这条）
export JAVA_HOME=/usr/local/jdk17
export MAVEN_HOME=/usr/local/maven
case ":$PATH:" in
  *":$JAVA_HOME/bin:"*) ;;
  *) export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH" ;;
esac

# 2) /usr/local/bin 下的软链（非登录 shell 走这条，因为它在默认 PATH 里）
java -> /usr/local/jdk17/bin/java
javac / jar / jps / keytool 同理
mvn -> /usr/local/maven/bin/mvn
```

验证：`ssh root@<IP> 'which java && which mvn'` 现在能直接解析。
`deploy.sh` 里仍然显式 export 一次（不依赖上面两层），因为脚本可能在
更极端的环境里被调用。

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
for s in item-service order-service search-service seckill-service; do
  n=$(docker exec $s sh -c 'cat /root/logs/csp/command-center.log.* 2>/dev/null | grep -c "Socket income"')
  echo "$s: $n"
done
```

计数会随时间增长（控制台每约 10 秒拉一次）。2026-09-02 修复后的实测：
`item-service 106 / order-service 105 / search-service 15 / seckill-service 107`。

> 注意：`search-service` 一开始是**完全没配** sentinel 的（`bootstrap.yaml`
> 里没有 `sentinel` 段），占位符落到 `localhost:8090` 所以连不上；`item-service`
> 和 `seckill-service` 则是端口写了两遍（`192.168.100.133:8090` 拼成
> `192.168.100.133:8090:8090`）。三个都已在源码里修好并重新部署，
> 详见 `deploy-assets/sentinel/README.md`。

### 6.7 ES 的 IK 分词插件（已装好，2026-09-02）

**原状**：`root_es-plugins` 卷里没有任何文件，`_cat/plugins` 为空，
索引 `products` 用的是标准分词器。

**现状**：IK 已安装并生效 ——

```bash
$ curl -s 'http://localhost:9200/_cat/plugins?v'
name         component   version
b7cdd1cba22c analysis-ik 7.12.1

$ curl -s -X POST 'http://localhost:9200/_analyze' \
    -H 'Content-Type: application/json' \
    -d '{"analyzer":"ik_smart","text":"华为手机秒杀活动"}'
华为 -> 手机 -> 秒杀 -> 活动          # 对比 standard：华 -> 为 -> 手 -> 机 -> ...
```

安装方式（用的是 ES 官方插件安装器，属主/权限自动正确，不用手工 chown）：

```bash
docker cp /root/haalan/deploy/deploy-assets/es/elasticsearch-analysis-ik-7.12.1.zip es:/tmp/ik.zip
docker exec es bin/elasticsearch-plugin install --batch file:///tmp/ik.zip
docker restart es          # 装完必须重启才生效
docker exec es bin/elasticsearch-plugin list      # 输出 analysis-ik
```

> 服务器宿主**没有 `unzip`**（`python3` 有），所以走容器内的安装器最省事。
> 重启 es 实测约 **30 秒**恢复（`curl localhost:9200` 返回 200），
> `products` 索引的 8 条数据完好无损。

#### 光装插件不够：索引也要用上 IK（2026-09-02 已完成）

装好插件只是让 `ik_max_word` / `ik_smart` **可被引用**，索引本身不会自动换分词器。
而且 mapping 里的 `analyzer` 一旦建好索引就**不能原地修改** —— 只能重建。

所以做了三步：

**第 1 步：让代码自动建对索引**

新增 `item-service/src/main/java/com/haalan/item/config/ProductIndexInitializer.java`
（`ApplicationRunner`，启动时检查 `products` 是否存在，不存在就用
`deploy-assets/es/products-mapping.json` 建好）。字段映射：

```json
"spuName": {
  "type": "text",
  "analyzer": "ik_max_word",        // 写入时细粒度切
  "search_analyzer": "ik_smart",    // 查询时粗粒度切
  "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
}
```

`brandName` / `categoryName` / `description` 同样处理。
注意 **`.keyword` 子字段必须保留** —— 排序和聚合还在用它。

**第 2 步：重建已有索引（用脚本，不动容器）**

```bash
bash scripts/reindex-products.sh --dry-run   # 先看它要干什么
bash scripts/reindex-products.sh -y          # 备份 → 删 → 按 IK mapping 重建 → reindex 回来
```

脚本顺序：现有 `products` 复制到 `products_pre_ik`（安全网，**保留不删**）
→ 删 `products` → 按 IK mapping 重建 → 从 `products_pre_ik` reindex 回来。

**回滚**（两条命令）：

```bash
curl -X DELETE 'localhost:9200/products'
curl -X POST 'localhost:9200/_reindex?wait_for_completion=true' -H 'Content-Type: application/json' \
  -d '{"source":{"index":"products_pre_ik"},"dest":{"index":"products"}}'
```

**第 3 步：验证**

```bash
$ curl -s 'localhost:9200/products/_mapping?pretty' | grep -A3 '"spuName"'
      "spuName" : {
        "type" : "text",
        "analyzer" : "ik_max_word",
        "search_analyzer" : "ik_smart"

$ curl -s 'localhost:9200/_analyze' -H 'Content-Type: application/json' \
    -d '{"analyzer":"ik_smart","text":"华为手机"}'
华为 / 手机          # 标准分词器会是 华 / 为 / 手 / 机
```

实测搜索 `keyword=华为` 返回 `华为 Mate 60 Pro`（`total: 1`），
`products` 8 条数据完好。

#### ⚠ 顺带修掉的一个隐藏 bug：ES 端口

排查「搜索报 500」时发现 `Connection refused`。根因在配置：

```yaml
haalan:
  es:
    host: es          # ← 端口是 -1
```

`EsProperties.host` 直接交给 `HttpHost.create()`，端口为 `-1` 时
Apache HttpClient 会退化成 **http 默认端口 80**，而 ES 监听 9200。
6 个服务的 `application-dev.yaml` 全部改成：

```yaml
  es:
    host: es:9200
```

> 这是**一直存在的 bug**，只是之前没人从容器内真正调过搜索接口。
> 修复前 `GET /api/product/search?keyword=华为` 返回
> `{"code":500,"message":"服务器内部错误"}`，修复后返回正常结果。

### 6.8 `.env` 里的 `COMPOSE_PROJECT_NAME=root` 是刻意设的

它让新编排算出 `root_haalan` 网络和 `root_*` 数据卷 —— 也就是**当前正在用的那套**，
从而复用现有数据（RabbitMQ 用户/vhost、ES 索引、Nacos 数据）。
**换机器时务必清空这一行**，否则会去连一台机器上不存在的资源。
详见**第七节「换服务器：数据卷怎么一并迁移」**。

### 6.9 ⛔ 别在本机改完代码就直接跑 `deploy.sh`（源码不同步的坑）

`deploy.sh` 在服务器上跑 `mvn`，编译的是 `/root/haalan` 里的源码。
**本机的改动不会自动出现在服务器上。**

最容易踩的场景：

```powershell
# 本机改完 LoginLogAspect.java，然后：
hd -NoSync user-service        # ← 或者手工 ssh 进去跑 deploy.sh
# 结果：容器重建了，但跑的还是旧代码 —— 改动看起来"没生效"
```

实测证据（2026-10-14）：本机与服务器各 313 个 `.java` 文件，其中
`user-service/src/main/java/com/haalan/user/aop/LoginLogAspect.java` 与
`.../service/Impl/TUserServiceImpl.java` 两个文件内容不同 —— 本机有未提交的改动，
服务器还是旧版。

**正确做法**：用 `hd`（默认先同步源码），或先单独跑 `.\sync-source.ps1`：

```powershell
hd user-service                          # 推荐：自动同步源码 + 部署
.\sync-source.ps1 -SyncOnly              # 或只同步，稍后再部署
```

判断源码是否同步的办法 —— 比对 MD5：

```powershell
# 本机
Get-FileHash D:\dswork\work1\haalan\user-service\src\main\java\com\haalan\user\aop\LoginLogAspect.java -Algorithm MD5
# 服务器
hs "md5sum /root/haalan/user-service/src/main/java/com/haalan/user/aop/LoginLogAspect.java"
```

两边一致才说明同步成功。实测 `hd` 同步后 8 个模块 **353/353 个文件 MD5 完全一致**。

### 6.10 服务器系统时钟曾慢 41 天（✅ 已于 2026-10-14 修好并做了持久化）

**问题**（2026-10-14 发现）：

```
本机:   2026-10-14 10:01:40
服务器: 2026-09-03 06:51:51 CST     ← 系统时间慢约 41 天（3553803 秒）
RTC:    2026-10-14 10:01:41          ← 硬件时钟是对的
```

`timedatectl` 显示 `NTP enabled: yes` 但 `NTP synchronized: no`，
`chronyc sources` 里 4 个源都是好的（`^*` / `^+`），`chronyc tracking` 却写着
`System time: 3553803.5 seconds slow of NTP time` —— **chronyd 在跑，就是追不上**。

**根因**在 `/etc/chrony.conf` 第 13 行：

```
makestep 1.0 3
```

`3` 的含义是「只在 chronyd 启动后的**前 3 次**校时里允许时间跳变，之后只能缓慢 slew」。
这台 VM 应该是被挂起过（或从快照恢复），系统时钟冻结在 9 月 3 日。
chronyd 启动于 9月02，那 3 次机会早用完了，此后只能以微秒级速度追赶 —— 41 天永远追不上。

症状：往服务器传源码时 `tar` 刷一屏警告
`时间戳 2026-10-14 00:50:36 是未来的 3522839 秒之后`（**不影响解包结果**，实测 353 个文件 MD5 全一致）。

**已做的修复**（两步）：

```bash
# 1. 立刻跳变到正确时间
chronyc makestep
#    2026-09-03 06:53:56  →  2026-10-14 10:03:53

# 2. 持久化：把 "makestep 1.0 3" 改成 "makestep 1.0 -1"
sed -i 's|^makestep 1.0 3$|makestep 1.0 -1|' /etc/chrony.conf
systemctl restart chronyd
```

`-1` 表示「任何时候偏移 >1 秒就直接跳变」，这样下次 VM 再被挂起也能自愈。
`/etc/chrony.conf` 原文件已备份到 `/root/clock-fix-20261014/chrony.conf.bak`。

修复后：

```
NTP enabled: yes
NTP synchronized: yes          ← 从 no 变成 yes
System time: 0.000679368 seconds fast of NTP time
```

**跳变的副作用（实测，已确认无影响）**：

| 项目 | 结果 |
|---|---|
| 17 个容器 | ✅ 全部存活，`Up 5 weeks`（只是显示变了） |
| Redis | ⚠️ 25 个 key 里 **23 个过期消失** —— 它们 TTL 只剩 ~16.6 天，前跳 41 天必然过期 |
| 消失的 key | 全是 `seckill:*` 缓存（stock/product/limit/activity），对应**活动 1**，其 `end_time` 是 **2026-09-23 已结束**，本来也该过期 |
| 存活的 key | `seckill:buy:15`、`seckill:buy:20`（无 TTL 的限购记录） |
| 各服务 ERROR | 时钟跳变后（10:03 起）**0 条** |
| 冒烟 | 前端 200 / 管理端 200 / Sentinel 200 / Nacos 200 / 网关 401 / ES 200 |
| Nacos 注册 | 6 个服务全部 `healthy: true` |
| ES 数据 | `products` 仍是 8 条 |
| MySQL | 7 个业务库齐全 |
| xxl-job | 跳变瞬间记了一条 `schedule misfire, jobId = 2`（`misfire_strategy: DO_NOTHING`，不补跑），之后按新时间正常每 10 分钟调度 |

> ⚠️ 如果将来再遇到「时钟大幅偏移」，**跳变前先给 Redis 落盘**（`BGSAVE`），
> 并记下 key 清单 —— 带 TTL 的 key 会不可逆地过期。快照脚本见本节改动前的做法：
> `/root/clock-fix-20261014/`（含 `before-date.txt`、`redis-keys-with-ttl.txt`、
> `before-activities.txt`、`before-containers.txt`、`chrony.conf.bak`）。

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
| Docker 卷 | `root_es-plugins` | ES 插件（**已装 analysis-ik**） | `tar` 打包 → 解包 |
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

实测产出（2026-09-02 真实跑出来的字节数）：

```
/root/haalan-backup/                        总计 25 MB
├── MANIFEST.txt                       430   卷名、属主、大小清单
├── SHA256SUMS                        1546   完整性校验（17 项）
├── mysql/all-databases.sql.gz     3444941   3.3 M ← 全部业务数据
├── volumes/redis-data.tar.gz         1503
├── volumes/es-data.tar.gz         2107412   2.0 M
├── volumes/es-plugins.tar.gz          107   （源目录是空的，所以只有 107 字节）
├── volumes/mq-data.tar.gz         3608576   3.4 M
├── volumes/mq-plugins.tar.gz     15733191   15 M
├── volumes/nacos-data.tar.gz       475344   464 K
├── binds/seata.tar.gz                7238   7.1 K
├── rabbitmq/definitions.json         7688   7.5 K
└── compose/                                 docker-compose.yml(11906)
                                             docker-compose.apps.yml(4607)
                                             .env(2666) images.txt(2347)
                                             containers.txt(743)
                                             volumes.txt(307) networks.txt(270)
```

> 注意压缩率：源卷 `mq-data` 349 MB → tar.gz 只有 **3.4 MB**，
> `nacos-data` 457 MB → **464 KB**（那 457 MB 几乎全是 `protocol/` 里的
> raft 日志，全是零字节，压缩后可以忽略）。所以整个备份只有 25 MB，
> 随便怎么传都行。
>
> 反过来说：**`tar` 卷目录 ≠ 可用的逻辑备份**。MySQL 之所以不走 tar，
> 见 7.1 的说明。

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
for s in item-service order-service search-service seckill-service; do
  echo "$s: $(docker exec $s sh -c 'cat /root/logs/csp/command-center.log.* 2>/dev/null | grep -c "Socket income"')"
done                                                  # Sentinel 四个都应有计数
```

> 注意 `docker compose ps` 在没有 `-f` 时只列**当前目录对应编排**的服务。
> 新机器上如果只放了 `deploy/docker-compose.yml`（完整版），在 `deploy/`
> 目录下执行就能看到全部 17 个。

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

### 7.7 定时备份（已配好）

`scripts/cron-backup.sh` 是 `backup-volumes.sh` 的定时包装，2026-09-02 已装进
root 的 crontab：

```bash
$ crontab -l
30 3 * * 0 cd /root/haalan/deploy && bash scripts/cron-backup.sh \
    --out /root/haalan-backup --keep 14 >> /root/haalan-backup/cron.log 2>&1
    # haalan-backup (deploy/scripts/cron-backup.sh)
```

即**每周日 03:30** 跑一次，保留 **14 天**。每次产出到
`/root/haalan-backup/<YYYY-MM-DD>/`，并把 `latest` 软链指向最新一份。

```bash
bash scripts/cron-backup.sh              # 手工立刻跑一次
bash scripts/cron-backup.sh --dry-run    # 只打印计划
bash scripts/cron-backup.sh --keep 30    # 改成保留 30 天（下次安装时生效）
bash scripts/cron-backup.sh --status     # 看 crontab + 备份目录 + 磁盘
bash scripts/cron-backup.sh --install    # 安装/更新 crontab（幂等，不会重复加）
bash scripts/cron-backup.sh --uninstall  # 移除
```

三个设计要点：

- **轮转只匹配 `YYYY-MM-DD` 形式的目录名**，绝不碰 `latest` 软链或别的文件。
- **脚本内部显式设置 `JAVA_HOME` / `PATH`** —— crond 不读 `/etc/profile`，
  靠登录环境会找不到 java。
- **轮转在备份成功之后才执行**；备份失败会保留现场并以非 0 退出，
  `cron.log` 里能看到原因。

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

1. **彻底统一编排** —— `/root/docker-compose.yml` 仍在给 12 个容器提供
   compose 标签。想彻底归一，需要一次「停起全部容器」的窗口把中间件也纳入
   `deploy/docker-compose.yml`（属于「修改容器」，要先确认）。
   **在那之前不要删 `/root/docker-compose.yml`。**
2. ~~**`search-service` 归队**~~ —— ✅ 已于 2026-09-02 完成，见 1.3 节。
3. ~~**给服务器装 JDK/Maven 的 PATH 兜底**~~ —— ✅ 已于 2026-09-02 完成：
   `/etc/profile.d/java.sh`（`JAVA_HOME` + 条件式追加 `PATH`）
   + `/usr/local/bin/{java,javac,jar,jps,keytool,mvn,mvnDebug}` 软链，
   非登录 shell 里 `which java` / `which mvn` 已能直接解析。见 6.4 节。
4. ~~**离线镜像包**~~ —— ✅ 已于 2026-09-02 完成，见 5.4 节：
   `bash scripts/pack-images.sh -o /root/haalan-images` 产出 12 个 `.tar.gz`
   （共 2.3 GB，含 sentinel 镜像），配 `load-images.sh` + `MANIFEST.txt` + `SHA256SUMS`。
5. **清理 `<none>` 悬空镜像** —— `docker system df` 显示可回收 5.7 GB
   （属于删除操作，需先确认；`docker image prune` 只删无标签镜像，不影响在用镜像）。
6. ~~**把备份做成定时任务**~~ —— ✅ 已于 2026-09-02 完成：
   `scripts/cron-backup.sh --install` 已写入 root 的 crontab
   （`30 3 * * 0`，每周日 03:30，保留 14 天，日志 `/root/haalan-backup/cron.log`），
   已真跑一次验证。见 7.7 节。
7. **给 `products` 之外的中文搜索调优** —— 目前只有 `spuName` / `brandName` /
   `categoryName` / `description` 用了 IK；`search-service` 的
   `ProductSearchServiceImpl` 仍是单个 `matchQuery("spuName")`，
   可以考虑 multi_match + 字段权重。属于功能改进，不是部署问题。
8. **把本机 `deploy.ps1` 的 SSH 凭据收好** —— 目前靠
   `_sshhelper/askpass.cmd` 明文放密码，只适合本地临时用；
   ~~长期建议换成 SSH 公钥~~ ✅ **已于 2026-10-14 完成**：本机 `id_ed25519.pub`
   已加入服务器 `/root/.ssh/authorized_keys`，`hs` / `hd` / `ssh haalan` 均为免密。
   遗留：`deploy.ps1` 里仍留着 ASKPASS 那几行（不影响使用，可后续清理）。
9. ~~修服务器系统时钟~~ ✅ **已于 2026-10-14 修好并持久化**，见 6.10 节。
