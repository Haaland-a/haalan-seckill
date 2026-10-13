# Sentinel 控制台（可选组件）

## 现状

`192.168.100.133:8090` **没有在监听**，Sentinel 控制台当前没有部署。
源目录里有现成的 jar：`E:\Personal\Desktop\jaalan\sentinel-dashboard.jar`（21.6 MB），
以及启动命令（`bu.txt`）：

```
java -Dserver.port=8090 \
     -Dcsp.sentinel.dashboard.server=localhost:8090 \
     -Dproject.name=sentinel-dashboard \
     -jar sentinel-dashboard.jar
```

注意：**微服务侧的 Sentinel 客户端是配好的**，只是控制台没起。
`item-service` 和 `order-service` 的 `application-dev.yaml` 里写着
`haalan.sentinel.transport.host: 192.168.100.133`（硬编码 IP），
`gateway`/`user-service`/`search-service` 用容器名或默认值。

---

## 两种做法

### 做法 A：用现成 jar 做一个镜像（推荐，离线可行）

在**服务器**上：

```bash
mkdir -p /root/sentinel && cd /root/sentinel

# 把 sentinel-dashboard.jar 传上来（从本机 scp）
# scp sentinel-dashboard.jar root@192.168.100.133:/root/sentinel/

cat > Dockerfile <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY sentinel-dashboard.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-Dserver.port=8090", \
            "-Dcsp.sentinel.dashboard.server=localhost:8090", \
            "-Dproject.name=sentinel-dashboard", "-jar", "app.jar"]
EOF

docker build -t haalan/sentinel-dashboard:1.8.6 .
```

⚠️ 如果服务器上没有 `eclipse-temurin:17-jre`，可以改用已有的 `btgoose/jdk17:latest`：

```bash
sed -i 's|eclipse-temurin:17-jre|btgoose/jdk17:latest|' Dockerfile
```

然后启动：

```bash
cd /root/haalan/deploy
docker compose --profile tools up -d sentinel-dashboard
```

访问 `http://192.168.100.133:8090`，默认账号密码都是 `sentinel`。

### 做法 B：直接用官方镜像

```bash
docker pull bladex/sentinel-dashboard:1.8.6
docker tag bladex/sentinel-dashboard:1.8.6 haalan/sentinel-dashboard:1.8.6
```

服务器能访问 Docker Hub 加速器，实测可以拉取镜像。

---

## 微服务侧要确认的事

控制台起来后，各微服务需要能连上它。检查 Nacos 里的配置：

- `item-service` / `order-service` 的 `application-dev.yaml`：
  `haalan.sentinel.transport.host: 192.168.100.133`
  → 这个 IP 是宿主机 IP，容器内能通，**不用改**。
- 但 `haalan.sentinel.transport.port` 如果没有配，默认是 8719。
  控制台是从**客户端主动上报**的，所以只要客户端能出网到 8090 就行。

**关键点**：`sentinel-dashboard` 容器接在 `haalan` 网络（即 `root_haalan`）上，
而各微服务也在同一网络，所以容器间用服务名互通没问题；
但客户端的 `transport.host` 写的是宿主机 IP，实际通信会走宿主机 8719 端口，
这部分是正常的（Sentinel 的既有行为）。

---

## 当前建议

**先不要装。** 理由：

1. 服务器只剩约 1 GB 可用内存，多一个 JVM（哪怕 `-Xmx256m`）会加重压力。
2. 当前部署流程（`deploy.sh`）已经能验证服务是否正常注册到 Nacos，
   不依赖 Sentinel 控制台。
3. 原编排里本来就没有它，说明之前也没在用。

需要做限流规则调试时再按上面做法 A 装。
