# 04 · Docker 架构与运行手册

> 一句话：**中间件放容器，应用在本地跑；答辩演示时才全栈进容器。**
> 一套 `docker-compose.yml`，两种用法，靠 profile 切换。

---

## 1. 为什么不做成"全容器化开发"

这是本次唯一的架构取舍，写清楚免得以后反复争。

| | 中间件容器化 + 应用本地跑（**选这个**） | 全容器化，日常也用它 |
|---|---|---|
| 改一行后端代码 | 保存 → 自动重启，**3 秒** | 重新 build 镜像，**3–5 分钟** |
| 断点调试 | IDE 直接连，正常 | 要配 remote debug，端口映射一堆 |
| 前端热更新 | Vite HMR，毫秒级 | 打包后看静态文件，改一次等一次 |
| 环境一致性 | 中间件版本完全一致，应用靠 `pom.xml` / `package.json` 锁 | 完全一致 |
| 首次上手 | 装 JDK + Node + Docker | 只装 Docker |

七周时间，六个人，每天改几十次代码。全容器化省下的那点"环境一致"，换来的是每人每天几十分钟的等待——**不划算**。

真正需要"完全一致"的场合只有一个：**答辩当天**。所以留了 `full` profile。

---

## 2. 拓扑

```
                    ┌─────────────────────────────────────┐
   浏览器 ──────────▶│  gateway  (nginx:1.27)  :8088       │
   (宿主机/局域网)    │    /      → 状态页 / 前端静态资源      │
                    │    /api/  → backend:8080            │
                    └──────────────┬──────────────────────┘
                                   │  pos-net（自定义 bridge）
                    ┌──────────────┴──────────────┐
                    │                             │
          ┌─────────▼─────────┐         ┌─────────▼─────────┐
          │ backend :8080      │────────▶│ redis   :6379      │
          │ Spring Boot 3.2    │         │ 缓存/幂等/预占键    │
          └─────────┬──────────┘         └────────────────────┘
                    │
          ┌─────────▼─────────┐         ┌────────────────────┐
          │ mysql   :3306      │◀────────│ adminer :8081      │
          │ MySQL 8.0.36       │         │ 网页版数据库客户端   │
          └────────────────────┘         └────────────────────┘
```

**只在开发时存在的**：adminer（方便查表）、backend 的 8080 端口映射（IDE 直连调试）。
**答辩时对外只有一个地址**：`http://<宿主机IP>:8088`。

---

## 3. 目录

```
.
├── docker-compose.yml          编排（profile: full 才起后端和网关）
├── .env.example                变量模板 → 复制成 .env
├── docker/
│   ├── mysql/
│   │   ├── conf.d/my.cnf       字符集/时区/严格模式/慢查询
│   │   └── initdb/00-init.sql  只建库授权，表结构交给 Flyway
│   ├── redis/redis.conf        AOF 持久化、内存上限、淘汰策略
│   └── nginx/
│       ├── conf.d/default.conf 反代规则、SPA 兜底、gzip
│       └── html/index.html     容器自检状态页
└── backend/
    ├── Dockerfile              多阶段构建（maven-alpine → jre-alpine）
    ├── maven-settings.xml      容器内构建走阿里云源（不进运行时镜像）
    ├── .dockerignore
    ├── pom.xml
    └── src/main/
        ├── java/com/supermarket/pos/
        │   ├── PosApplication.java
        │   └── health/HealthController.java
        └── resources/application.yml
```

---

## 4. 怎么跑

### 4.1 日常开发（写代码时用这个）

```bash
cp .env.example .env          # 第一次
docker compose up -d          # 只起 mysql + redis + adminer
```

然后后端在 IDE 里跑 `PosApplication`，前端 `npm run dev`。

验证：

```bash
docker compose ps                                  # 三个服务都 healthy
curl localhost:8080/api/health                     # 后端起来后
```

数据库客户端：<http://localhost:8081> → 系统 `MySQL` → 服务器 `mysql`（或 `localhost`）→ 用户 `pos` → 密码见 `.env`。

### 4.2 答辩演示 / 全栈验收

```bash
docker compose --profile full up -d --build
```

首次 build 要拉 5 个镜像 + 编译后端，**约 5–10 分钟**（网速决定）。之后改代码只重建 backend 一层。

打开 <http://localhost:8088>，看到状态页三行全绿就通了。

### 4.3 停止与清理

```bash
docker compose down                 # 停容器，保留数据
docker compose --profile full down  # 全栈停
docker compose down -v              # 连数据卷一起删（演示前重置数据用）
docker compose logs -f backend      # 跟日志
```

---

## 5. 端口表

| 服务 | 容器内 | 宿主机 | 说明 |
|---|---|---|---|
| gateway | 80 | **8088** | 唯一对外入口。用 8088 是因为 80 常被占且 Linux 上要 root |
| backend | 8080 | 8080 | 开发时 IDE 直连；演示时也可直接访问 |
| mysql | 3306 | 3306 | 改 `MYSQL_PORT` 可避让已有 MySQL |
| redis | 6379 | 6379 | 改 `REDIS_PORT` |
| adminer | 8080 | 8081 | 网页数据库客户端 |

---

## 6. 七个坑（都是会真踩到的）

**① `lower_case_table_names=1` 只在初始化时生效。**
改完 `my.cnf` 发现没效果，是因为数据卷已经存在。必须 `docker compose down -v` 重建。这一条**只在头几天定，后期不许改**——一旦有数据在里面，改它很麻烦。

**② `.env` 里的密码别提交。**
`.env` 已被 `.gitignore` 排除，`.env.example` 会进仓库。有人手贱 `git add -f .env`，密码就永久留在历史里了，改文件也删不掉。

**③ MySQL 第一次启动要 30–60 秒。**
`healthcheck` 的 `start_period: 45s` 就是给它留的。这期间 `docker compose ps` 显示 `starting` 是正常的，别急着 `restart`。

**④ 后端连不上 MySQL 时会启动失败并重启循环。**
`depends_on: condition: service_healthy` 已经保证顺序。如果还循环，先看 `docker compose logs mysql`——通常是密码或字符集配置写错了。

**⑤ 8088 打不开，但 8080 能开。**
说明网关没起来，或者 nginx 配置语法错。`docker compose logs gateway` 会直接告诉你哪一行写错了。

**⑥ Windows 上挂载目录如果路径含中文，MySQL 可能起不来。**
项目目录建议放纯英文路径。团队统一用 `D:\pos` 这类路径最省事。

**⑦ 时区。**
容器、MySQL、JVM 三处都设了 `Asia/Shanghai`。如果发现时间差 8 小时，查这三处哪一处漏了——**订单时间错了，报表全错**。

---

## 7. 在 Kali 里跑（当前验证环境）

这台 Windows 是 LTSC 2019（Build 17763），**装不了 Docker Desktop**（需要 19044+，且没有 WSL）。所以容器实际跑在 VMware 的 Kali 里。

Kali 的 IP 是 `192.168.19.128`（VMnet8）。宿主机浏览器直接访问 `http://192.168.19.128:8088` 即可。

需要注意两点：

1. **路径不能跨系统直接挂载**。把项目目录整个拷进 Kali 再用，别指望 `/mnt/hgfs` 共享目录——MySQL 对共享文件夹的文件锁支持很差，会起不来。
2. **拉镜像可能慢**。Kali 里如果不能直连 Docker Hub，配 `/etc/docker/daemon.json`：
   ```json
   { "registry-mirrors": ["https://docker.m.daocloud.io"] }
   ```
   然后 `systemctl restart docker`。

---

## 8. 下一步（按优先级）

| 顺序 | 事项 | 归属 | 说明 |
|---|---|---|---|
| 1 | 42 张表 DDL + Flyway 接入 | A | `backend/src/main/resources/db/migration/V1__init.sql`，从这里开始有版本记录 |
| 2 | 公共返回体 `Result<T>` + 全局异常 | A | 所有接口统一外壳，前端只写一次解析 |
| 3 | 三个前端脚手架 + 各自 Dockerfile | E / F | 挂到 gateway 的 `/`、`/pos/`、`/h5/`，需要给 Vite 配 `base` |
| 4 | 演示数据脚本 | F | `demo-base.sql` + `demo-orders.sql`，配 `POST /demo/reset` |
| 5 | 把 `full` profile 接进答辩流程 | A | 提前一周演练，别当天第一次跑 |

---

## 9. 已经踩过的坑（实测记录）

这三个是真跑起来时撞到的，不是推测。记下来省得六个人各撞一次。

### ① `maven:3.9-eclipse-temurin-17` 拉不下来

jammy 变体约 900MB，国内镜像源拉这个标签会长时间无响应（实测 12 分钟零进展，最后只能中断）。

**换成 alpine 变体**：`maven:3.9-eclipse-temurin-17-alpine` 88 秒下完，最终镜像也从 ~600MB 降到 ~250MB。已经在 Dockerfile 里固化。

### ② alpine 里 `apk add curl` 会失败

`dl-cdn.alpinelinux.org` 在国内经常 TLS 握手失败：

```
WARNING: fetching https://dl-cdn.alpinelinux.org/alpine/v3.24/main/x86_64/APKINDEX.tar.gz: TLS: unspecified error
ERROR: unable to select packages: curl (no such package)
```

**结论：容器里不要装任何包。** 镜像自带的 `wget`（busybox）足够做健康检查，`/usr/share/zoneinfo` 也是完整的，配 `TZ` 和 `-Duser.timezone` 就能拿到中国时区。少一次 `apk`，就少一个构建失败点。

顺带一个：alpine 的 `adduser` 是 busybox 版，家目录参数是 **`-h`** 不是 `-d`，写错直接报 `unrecognized option`。

### ③ `rm -rf` 了被 bind-mount 的目录，容器里看到空目录

改配置后为了干净，执行了 `rm -rf docker/ && tar -x` 重新解压。结果宿主机上 `index.html` 明明在，容器里 `ls` 却是 **空的**，访问首页 403。

原因：容器挂载的是那个目录的 **inode**，目录被删重建后 inode 变了，容器还指着已删除的旧 inode。

**解法**：`docker compose up -d --force-recreate gateway`。

**预防**：同步代码时只覆盖文件，别 `rm -rf` 被挂载的目录（`docker/nginx/html`、`docker/mysql/conf.d` 这几个）。

---

## 10. 验证记录

**已实测通过（Kali，2026-10-25）**：

| 项目 | 结果 |
|---|---|
| 镜像拉取 | mysql 113s / redis 36s / nginx 58s / maven-alpine 88s / temurin-jre 149s |
| 编排语法 | `docker compose config` 通过 |
| MySQL 初始化 | `pos` + `pos_test` 建库成功，`sys_init_marker` 写入 |
| MySQL 参数 | utf8mb4 / utf8mb4_0900_ai_ci / +08:00 / lower_case_table_names=1 / 严格模式 |
| Redis | `PONG`，`aof_enabled:1` |
| 后端构建 | 依赖下载 91s，编译 10.6s，镜像 `pos/backend:local` |
| 全栈启动 | 5 个容器全部 healthy |
| 接口 | `GET /api/health` → `status:UP`，MySQL `8.0.36 / pos`，Redis `PONG` |
| 状态页 | `GET /` → 200，8.4KB |
| Adminer | `:8081` → 200 |

**未验证**：Windows 宿主机直连（本机无法安装 Docker）；三个前端接入 gateway 的路径规则（前端还没建）；`full` 形态在答辩机器上的实际表现。

