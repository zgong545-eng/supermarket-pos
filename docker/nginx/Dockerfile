# ============================================================
#  前端网关镜像：一起把 Vue 打包进去
#
#  为什么不分开两个镜像（前端一个、nginx 一个）：
#  前端产物离不开 nginx 配置，nginx 配置也只为这个前端服务。
#  合成一个镜像的好处是 `docker compose --profile full up -d --build`
#  一条命令就能从源码到可访问的页面，答辩现场不会漏步骤。
#
#  构建上下文是仓库根目录（见 docker-compose.yml 的 context: .），
#  因为要同时拷 frontend/ 和 docker/nginx/。
# ============================================================

# ---------- 阶段 1：构建前端 ----------
FROM node:22-alpine AS build

WORKDIR /app

# 先只拷依赖清单：代码改了不会作废依赖层缓存
COPY frontend/package.json frontend/package-lock.json* ./

RUN npm install --registry=https://registry.npmmirror.com --no-audit --no-fund

COPY frontend/ ./
RUN npm run build


# ---------- 阶段 2：nginx 托管 ----------
FROM nginx:1.27-alpine

# 时区与中文环境
ENV TZ=Asia/Shanghai

COPY docker/nginx/conf.d /etc/nginx/conf.d
COPY --from=build /app/dist /usr/share/nginx/html
# 自检状态页：/status.html
COPY docker/nginx/html/status.html /usr/share/nginx/html/status.html

EXPOSE 80
