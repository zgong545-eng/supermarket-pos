# ============================================================
#  后端镜像 · 多阶段构建
#    构建阶段：Maven 编译打包（这一层镜像 ~500MB，只在构建时用）
#    运行阶段：只带 JRE + jar（~230MB）
#
#  为什么要分两阶段：把 JDK、源码、Maven 仓库留在构建层，
#  最终镜像里只有 jar。镜像小 = 换机器拉取快，答辩当天不用等。
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom，让依赖层能被缓存
# 改业务代码不会让这一层失效，第二次以后构建只需十几秒
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre-jammy

# 健康检查要用 curl；jammy 基础镜像里没有
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

# 不用 root 跑应用：容器内被攻破也拿不到宿主权限
RUN groupadd -r app && useradd -r -g app -d /app app

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
