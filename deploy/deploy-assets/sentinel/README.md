# Sentinel 控制台

## 现状：**已经在跑**（2026-09-02 落地）

```
容器    sentinel-dashboard
地址    http://192.168.100.133:8090
账号    sentinel / sentinel
网络    root_haalan（与应用容器同网）
```

验证：

```bash
docker ps --filter name=sentinel-dashboard
curl -I http://192.168.100.133:8090/          # HTTP 200
```

> 控制台的 REST API（`/api/app/brief` 等）**需要登录**，直接 curl 会返回
> 401 + `Set-Cookie: sentinel_dashboard_cookie=...`。这是正常的安全行为，
> 不是故障。想看「控制台有没有连上客户端」，看客户端容器里的日志：
> ```bash
> docker exec order-service tail -20 /root/logs/csp/command-center.log.*
> # 出现 Socket income: GET /metric?... addr: /172.19.0.18 即已连通
> ```

---

## 这个目录里有什么

| 文件 | 说明 |
|---|---|
| `sentinel-dashboard.jar` | 1.8.6，21.6 MB，官方 release 包（`git add -f` 才能提交，被 `.gitignore` 的 `*.jar` 挡住） |
| `Dockerfile` | 用它构建镜像 |
| `run-sentinel-dashboard.txt` | 原始启动命令（来自源目录 `bu.txt`） |

jar 的来源：`E:\Personal\Desktop\jaalan\sentinel-dashboard.jar`。
校验值 SHA256 `E7331C6F900B3568638EC43ACB4E1FE124618D025027491720E032C9AF50035C`。

---

## 怎么部署

**统一用脚本**，不要手工敲：

```bash
cd /root/haalan/deploy

bash scripts/deploy-sentinel.sh build     # 构建镜像
bash scripts/deploy-sentinel.sh up        # 起容器
bash scripts/deploy-sentinel.sh status    # 看状态（并核对 8 个中间件未受影响）
bash scripts/deploy-sentinel.sh logs      # 跟日志
bash scripts/deploy-sentinel.sh down      # 停掉（不动别的容器）
bash scripts/deploy-sentinel.sh all       # build + up + status
```

镜像名 / 容器名 / 端口由 `.env` 的 `SENTINEL_IMAGE`、`SENTINEL_CONTAINER`、
`SENTINEL_PORT` 控制。

### 为什么用 `docker run` 而不是 `docker compose --profile tools up -d`

`docker-compose.yml` 里保留着 `sentinel-dashboard` 的服务定义（`profiles: [tools]`），
但在**当前这台服务器上不能用**：

```
DRY-RUN MODE - sentinel-dashboard Pulling
Error  lookup registry-1.docker.io on 192.168.100.2:53: no such host
```

compose 起服务前会先尝试 pull `haalan/sentinel-dashboard:1.8.6`，而服务器
直连 `registry-1.docker.io` 是 DNS 失败。关键在于：**compose 的 pull 路径不走
`/etc/docker/daemon.json` 里配的加速器**，所以即使 `docker pull` 能成功，
compose 也会失败。

`deploy-sentinel.sh` 的办法是：先 `docker build` 出本地镜像，再 `docker run`
起容器 —— 完全绕开 pull。那个 `tools` profile 的定义留着，等换到能直连
Docker Hub 的机器上可以直接 `docker compose --profile tools up -d`。

---

## Dockerfile 里的两个关键点

```dockerfile
FROM btgoose/jdk17:latest
```

不用 `eclipse-temurin:17-jre`，因为**服务器上没有这个镜像**，而拉取又要走
Docker Hub。`btgoose/jdk17:latest` 是本地现成的（500 MB 层已缓存），
实际新增只有约 22 MB。

```dockerfile
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=160m \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  ... (共 10 个)"
```

**这 10 个 `--add-opens` 不能删。** Sentinel 1.8.6 是 2022 年用 JDK 8 编译的
Spring Boot 2.5.12 产物，在 JDK 17 下不加这些参数会抛
`InaccessibleObjectException`（JDK 9+ 的模块系统默认禁止反射访问 JDK 内部类）。
实测加上后启动正常，`Started DashboardApplication in 2.814 seconds`。

---

## 哪些服务会出现在控制台里

**只有 4 个**：`item-service`、`order-service`、`search-service`、`seckill-service`。

原因：这 4 个模块都依赖 `haalan-api`，而 `haalan-api/pom.xml` 里有
`spring-cloud-starter-alibaba-sentinel`。`gateway` 和 `user-service` 只依赖
`haalan-common`，**没有** sentinel 依赖，所以不会向控制台注册。

**这是设计使然，不是故障。** 如果想让网关也接入 Sentinel，需要额外引入
`spring-cloud-alibaba-sentinel-gateway` + `sentinel-spring-cloud-gateway-adapter`，
而服务器 `.m2` 里**没有**这两个制品（离线构建会失败），需要先联网补齐依赖。

---

## 客户端侧配置（已修复的一个 bug）

三个服务的 `bootstrap.yaml` 都有：

```yaml
spring:
  cloud:
    sentinel:
      eager: true
      filter:
        enabled: true
      transport:
        dashboard: ${haalan.sentinel.transport.host:localhost}:8090
```

注意末尾的 `:8090`。所以 `haalan.sentinel.transport.host` **只能填主机名/IP，
不能带端口**。

**修复前**的 `item-service` / `seckill-service` 的 `application-dev.yaml` 写的是
`host: 192.168.100.133:8090`，拼出来的地址是 `192.168.100.133:8090:8090`。
Sentinel 的 `TransportConfig` 只接受恰好一个冒号的地址，多出来的直接丢弃，
于是客户端日志出现：

```
WARNING Parse port of dashboard server failed: 192.168.100.133:8090:8090
WARNING [SimpleHttpHeartbeatSender] Dashboard server address not configured or not available
```

**修复后**三个服务统一写成容器名：

```yaml
haalan:
  sentinel:
    transport:
      host: sentinel-dashboard      # 不要带端口
```

用容器名的好处：换服务器 IP 不用改配置；且 `sentinel-dashboard` 容器就接在
`root_haalan` 网络上，各服务按容器名直连 8090 即可。

> 端口 8719 是**客户端**的命令端口（`command-center.log` 里会打印
> `Begin listening at port 8719`），由控制台主动来连，不需要在编排里映射。
