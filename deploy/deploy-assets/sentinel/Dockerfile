# =============================================================================
#  Sentinel 控制台 1.8.6 镜像
#  ---------------------------------------------------------------------------
#  基础镜像选 btgoose/jdk17:latest，原因：这台服务器**没有** eclipse-temurin /
#  openjdk 这类镜像，而 compose 的 pull 不走 /etc/docker/daemon.json 里的加速器
#  （实测报 `lookup registry-1.docker.io on 192.168.100.2:53: no such host`），
#  所以只能用本地已有的镜像。btgoose/jdk17 是 6 个后端镜像在用的那个。
#
#  构建上下文里必须有 sentinel-dashboard.jar（21.6 MB，来自
#  E:\Personal\Desktop\jaalan\sentinel-dashboard.jar）。该文件被
#  haalan/.gitignore 的 `*.jar` 规则忽略，不会进 git。
#
#  构建：bash deploy/scripts/deploy-sentinel.sh build
#     或 docker build -t haalan/sentinel-dashboard:1.8.6 .
# =============================================================================
FROM btgoose/jdk17:latest

WORKDIR /app

COPY sentinel-dashboard.jar app.jar

EXPOSE 8090

# Sentinel 1.8.6 的 dashboard 是 2022 年的产物（Spring Boot 2.5.12，用 JDK 8 编译），
# 在 JDK 17 上跑需要放开几个反射访问，否则启动阶段会抛
# InaccessibleObjectException: Unable to make ... accessible。
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=160m \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.net=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.management/java.lang.management=ALL-UNNAMED \
--add-opens java.management/sun.management=ALL-UNNAMED"

# -Dcsp.sentinel.dashboard.server 指向自己，这样控制台自身的 Sentinel 客户端
# 也会上报（Sentinel 官方启动脚本就是这么写的）。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=8090 -Dcsp.sentinel.dashboard.server=localhost:8090 -Dproject.name=sentinel-dashboard -jar app.jar"]
