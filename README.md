# 会员仓储超市 · 收银管理系统

软件工程课程设计（2024 级 · 6 人小组）结课项目。
一个门店、三种角色、三端界面：**会员手机端（H5）**、**收银员收银台（POS）**、**经理管理端**。

不是原型图，是能真跑的系统：库存会预占、支付回调做了幂等、优惠按商品逐行分摊、
金额全程用「分」计算、数据库结构由 Flyway 管控。

---

## 一、技术栈

| 层 | 选型 | 为什么 |
| --- | --- | --- |
| 后端 | Java 17 + Spring Boot 3.2 + Spring Security 6 | 课程要求 Java；Security 的 `@PreAuthorize` 正好演示细粒度权限 |
| 持久层 | MyBatis-Plus 3.5 + MySQL 8 | 单表 CRUD 不用写 SQL，复杂查询直接写 SQL，两不耽误 |
| 缓存 | Redis 7 | 演示用，缓存字典与热点商品 |
| 库版本 | Flyway | 表结构只允许迁移脚本改，杜绝"我本地是好的" |
| 前端 | Vue 3 + Vite 5 + Element Plus + Vant 4 + ECharts 5 | 一套工程三端复用，靠路由分组区分 |
| 部署 | Docker + Docker Compose + nginx | 一条命令起全栈，答辩换机器不慌 |

架构是**模块化单体**，不是微服务。理由写在 `docs/01-系统设计与答辩大纲.md`：
6 个人 7 周，拆微服务只会把时间花在联调和部署上。

---

## 二、目录结构

```
.
├── backend/                     后端（Spring Boot）
│   └── src/main/java/com/supermarket/pos/
│       ├── common/              统一返回体、异常、错误码、单号、登录上下文
│       ├── security/            JWT 签发校验、权限过滤器
│       ├── config/              MyBatis-Plus、数据初始化、演示数据自动灌入
│       └── module/
│           ├── system/          M01 平台权限（登录、用户、角色）
│           ├── product/         M02 商品
│           ├── stock/           M03 库存（预占 / 释放 / 实扣）
│           ├── member/          M04 会员
│           ├── promotion/       M05 促销（纯函数引擎 + 分摊算法）
│           ├── order/           M06 订单（状态机）
│           ├── pay/             M07 支付（幂等三道闸）
│           ├── shift/           M08 收银作业（交班、挂单）
│           ├── report/          M09 报表
│           ├── h5/              M10 会员端接口
│           ├── admin/           M12 管理端接口
│           └── demo/            演示数据（重置 / 灌 30 天历史订单）
├── frontend/                    前端（Vue 3，三端合一个工程）
│   └── src/
│       ├── api.js               所有接口集中封装
│       ├── session.js           登录态、权限判断、金额格式化
│       ├── router.js            三端路由分组
│       ├── layouts/             管理端 / 收银端 / H5 三套布局
│       └── views/
│           ├── admin/           10 个管理页面
│           ├── pos/             收银台、本班订单、交班
│           └── h5/              首页、购物车、订单、我的
├── docker/                      中间件配置与网关镜像
├── docker-compose.yml           编排（默认只起中间件，`--profile full` 起全栈）
└── docs/                        设计文档、分工手册、运行手册
```

---

## 三、快速开始

### 3.1 一条命令（推荐，答辩用）

前提：装了 Docker 与 Docker Compose。

```bash
cp .env.example .env          # 第一次要复制，按需改密码
docker compose --profile full up -d --build
```

首次构建约 3–5 分钟（下载依赖），之后改代码重建约 20 秒。
启动完成后：

| 入口 | 地址 |
| --- | --- |
| 前端（登录页） | http://localhost:8088 |
| 后端接口 | http://localhost:8088/api/... |
| 运行自检页 | http://localhost:8088/status.html |
| Adminer（看库） | http://localhost:8081 |

**演示账号**（首次启动自动创建）：

| 账号 | 密码 | 角色 | 能进哪 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 管理端全部 |
| `manager` | `manager123` | 经理 | 管理端（除改用户） |
| `cashier` | `cashier123` | 收银员 | 收银台 + 查商品 |

会员端不用账号：手机号 `13800138000` + 验证码 `1234`（演示版不发短信，没注册会自动开通）。

### 3.2 日常开发（改代码即时生效）

```bash
# 只起 MySQL / Redis / Adminer
docker compose up -d

# 后端：IDE 里跑 PosApplication，或命令行
cd backend && mvn spring-boot:run

# 前端：热更新
cd frontend && npm install && npm run dev     # http://localhost:5173
```

`vite.config.js` 已把 `/api` 代理到 `localhost:8080`，前端不用配跨域。

### 3.3 空库自动准备演示数据

`pos.demo.enabled=true`（默认开）时，只要库里没有商品，
启动会自动灌：60 个 SKU、3 个会员、2 个活动、3 种券、30 天约 300 笔历史订单。
所以第一次打开报表就能看到曲线，不会一片空白。

管理端「经营看板」底部还有三个按钮，可以手动重置 / 生成订单 / 刷新预聚合。

> 上线前把 `DEMO_ENABLED` 设成 `false`，否则任何人都能调 `/api/demo/reset` 清库。

---

## 四、三端入口

| 端 | 路由 | 说明 |
| --- | --- | --- |
| 管理端 | `/#/admin/dashboard` | 看板、商品、库存补货、会员、促销、订单、交班、报表、员工权限、系统设置 |
| 收银端 | `/#/pos/cashier` | 扫码开单、会员识别、挂单取单、四种支付、小票、交班对账 |
| 会员端 | `/#/h5/home` | 商品浏览、购物车、下单支付、订单、积分储值券 |

登录后按角色自动跳转：会员 → H5，收银员 → POS，管理员/经理 → 管理端。

---

## 五、四个"设计含量"最高的地方

答辩时如果只讲 CRUD 会很平淡，这四点是能深聊的：

### 1. 库存三态 + 单条条件更新

```
on_hand = available + locked        ← 恒等式，任何时候都必须成立
```

下单时 `available → locked`（预占），付款时 `locked → 出库`（实扣），取消时 `locked → available`（释放）。
全部用**一条带条件的 UPDATE** 完成，不做"先查后改"：

```sql
UPDATE inv_stock SET available = available - ?, locked = locked + ?
 WHERE sku_id = ? AND store_id = ? AND available >= ?
```

影响 0 行就是库存不足，直接报清楚"当前可售 N 件"。
管理端「库存 → 一致性自检」会现场验证恒等式，证明并发下账没算错。

### 2. 支付幂等的三道闸

| 闸 | 作用 |
| --- | --- |
| `UPDATE pay_payment SET status='SUCCESS' WHERE pay_no=? AND status='INIT'` | 只有第一次能改成功 |
| `channel_trade_no` 唯一索引 | 同一个渠道流水号插不进第二条 |
| 订单状态机 | `PAID` 不能再付一次 |

收银台的「连点两次（验幂等）」按钮就是演示这个：第二次会返回
`accepted=false, 回调次数=2`，钱不会重复扣。

### 3. 优惠分摊

整单优惠要摊到每一行，退货才知道每件该退多少。算法三步：
按行金额比例取整 → 小数部分从大到小补余数 → 仍有差额按行顺序找空间。
保证 `Σ分摊 = 整单优惠` 且任何一行不会被摊成负数。

优惠链固定顺序：**标准价 → 会员价 → 活动 → 券 → 积分抵扣**。

### 4. 订单状态机

7 个状态、9 条合法转移，表驱动写成 `switch`，非法转移直接抛异常。
两条最容易写错的规则：`PAID` 不能回到 `CANCELLED`（钱已经收了，只能退）；终态不能再变。

---

## 六、接口约定

- 统一返回：`{ "code": 0, "message": "ok", "data": {...} }`，`code != 0` 即业务失败
- 分页返回：`{ total, page, pageSize, records }`，不直接吐 MyBatis-Plus 的 `IPage`
- 金额：全部 `long`，单位**分**，前端只负责除以 100 显示
- 时间：`yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`
- 单号：`SO/PY/RF/IN/LS/CK/SH/HD/SF + yyyyMMdd + 5 位`，由 `sys_sequence` 行锁生成
- 错误码分段：`1xxxx` 通用 / `2xxxx` 权限 / `3xxxx` 商品库存 / `4xxxx` 交易支付 / `5xxxx` 会员营销 / `9xxxx` 系统

---

## 七、数据库

43 张表，按模块前缀分组：`sys_`（平台/系统）、`pms_`（商品）、`inv_`（库存）、
`mem_`（会员）、`pmt_`（促销）、`so_`（订单）、`pay_`（支付）、`pos_`（收银作业）、`rpt_`（报表）。

结构在 `backend/src/main/resources/db/migration/V1__init_schema.sql`，
初始化数据（角色、33 个权限点、字典、支付渠道）在 `V2__init_data.sql`。
**改表结构请新增 `V3__xxx.sql`，不要改 V1/V2**——已经跑过的脚本改了会导致校验失败。

---

## 八、文档

| 文件 | 内容 |
| --- | --- |
| `docs/00-团队执行手册.md` | 技术栈、公共契约、13 模块难度表、六人分工与 84 项任务、七周排期、DoD |
| `docs/01-系统设计与答辩大纲.md` | 设计依据，对应答辩报告 8 个评分项 |
| `docs/02-报告书正文-第1至3章.md` | 立项 / 可行性 / 需求分析成稿 |
| `docs/03-Git协作约定.md` | 分支、提交格式、review、冲突处理 |
| `docs/04-Docker架构与运行手册.md` | 架构取舍、端口、踩过的七个坑、验证记录 |
| `docs/05-团队协作接入与生产实践.md` | **组员接入清单** + 真实生产环境的 CI/CD、密钥、可观测性与回滚 |
| `docs/超市收银管理系统-设计与分工说明.docx` | 给非技术同学看的说明（分工与难度） |

---

## 九、已知边界

课堂演示版刻意不做的部分，答辩时主动说清楚比被问住好：

- **没有真实支付通道**：微信/支付宝是模拟回调，但回调后的逻辑与真实一致
- **没有短信**：验证码固定 `1234`
- **没有供应商与采购单**：补货由经理在后台直接录入，但仍写单据、改库存、留流水
- **没有拆零称重的电子秤对接**：称重商品由收银员手输重量
- **单门店**：表里留了 `store_id`，多店只需加数据，代码不用改
