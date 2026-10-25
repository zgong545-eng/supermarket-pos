# ============================================================
#  后端镜像 · 多阶段构建（alpine 变体）
#    构建阶段：Maven 编译打包（只在构建时用，不进最终镜像）
#    运行阶段：只带 JRE + jar
#
#  为什么用 alpine 变体：官方 jammy 变体 maven 镜像约 900MB，
#  alpine 约 545MB。国内拉取慢的时候，体积就是时间。
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Maven 走国内镜像源。不配这个，构建会卡在 Maven Central 超时上
COPY maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom，让依赖层能被缓存：改业务代码不会让这一层失效
COPY pom.xml .
RUN mvn -B -q -s /root/.m2/settings.xml dependency:go-offline

COPY src ./src
RUN mvn -B -q -s /root/.m2/settings.xml clean package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre-alpine

# 不装任何东西：
#   · 健康检查用镜像自带的 busybox wget（/usr/bin/wget），不用 curl
#   · 时区数据 zoneinfo 已在基础镜像里，配 TZ / -Duser.timezone 即可
#   · 装包需要连 alpine CDN，国内经常 TLS 失败，能省就省
# 少一次 apk add，构建就少一个失败点

# 不用 root 跑应用：容器内被攻破也拿不到宿主权限
# 注意：alpine 的 adduser 是 busybox 版，家目录用 -h，不是 Debian 的 -d
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/app.jar /app/app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage 而不是写死 -Xmx：
# 容器内存限制改了不用改镜像，JVM 自己按 cgroup 限制算堆
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=70", \
            "-Duser.timezone=Asia/Shanghai", \
            "-Dfile.encoding=UTF-8", \
            "-jar", "/app/app.jar"]
