# 03 · Git 协作约定

> 一句话：**`main` 只放"能跑"的代码，任何人都不直接往里推。**
> 这份文件解决"六个人怎么不互相踩脚"，不涉及业务设计。业务口径看 `00-团队执行手册.md`。

---

## 1. 仓库地址与角色

| 远端名 | 平台 | 角色 | 说明 |
|---|---|---|---|
| `origin` | GitHub（**公开**） | **主库** | 所有开发以此为准，PR、Issue、Actions 都在这里。公开是为了让老师/助教直接看代码与提交记录 |
| `gitee` | Gitee 码云 | **镜像备份** | 只做单向上推，**不在 Gitee 上开发** |

配置方式（一次搞定，之后 `git push` 会同时推两边）：

```bash
git remote add origin git@github.com:<你的用户名>/supermarket-pos.git
git remote add gitee  git@gitee.com:<你的用户名>/supermarket-pos.git

# 让 origin 一次推到两个地址
git remote set-url --add --push origin git@github.com:<你的用户名>/supermarket-pos.git
git remote set-url --add --push origin git@gitee.com:<你的用户名>/supermarket-pos.git
```

> Gitee 的 SSH 走标准 22 端口，它国内直连没问题，不需要特殊配置。

---

## 2. 第一次拉代码

```bash
git clone git@github.com:<你的用户名>/supermarket-pos.git
cd supermarket-pos
git config user.name  "你的姓名"
git config user.email "你的邮箱"
```

**⚠️ Windows 组员必看**：`github.com` 的 443 端口在国内经常连不上，直接 clone 会卡住几分钟然后超时。解决办法是把 SSH 改走 443 端口——在 `C:\Users\<你>\.ssh\config` 里加四行：

```
Host github.com
  HostName ssh.github.com
  Port 443
  User git
```

配完执行 `ssh -T git@github.com`，看到 `Hi <用户名>! You've successfully authenticated` 就通了。这一步在组长机器上已经配好，其他人照抄即可。

---

## 3. 分支约定

| 分支 | 谁能推 | 存活期 | 说明 |
|---|---|---|---|
| `main` | **没人能直接推** | 永久 | 受保护分支，只能通过 PR 合入；任何时候拉下来都能启动 |
| `feature/T-A12-stock-lock` | 负责人自己 | 合并后即删 | 一人一分支，名字带任务号 |
| `fix/T-C08-refund-amount` | 负责人自己 | 合并后即删 | 修 bug 用 |
| `docs/report-ch3` | 文档执笔人 | 合并后即删 | 只改 `docs/` 下的内容 |

**任务号取 `00-团队执行手册.md` 里的 84 项任务编号**（`T-A01` … `T-F13`）。这样看到分支名就知道是谁的活、对应哪一项，答辩时也拿得出证据。

```bash
git checkout -b feature/T-B12-stock-lock
```

> **`main` 的保护规则必须真的配上，否则上面这张表只是口号。**
> 具体点击路径见 `05-团队协作接入与生产实践.md` 第 2 节第 ② 步：禁直推、必须 PR、必须 1 个 approve、必须 CI 四项全绿、禁 force push、禁删除分支。

---

## 4. 提交信息格式

```
类型(范围): 一句话说清做了什么
```

- **类型**：`feat` 新增功能 / `fix` 修 bug / `refactor` 重构 / `test` 测试 / `docs` 文档 / `chore` 杂务
- **范围**：模块号 `M03`、或任务号 `T-B12`、或 `docs`、`ci`

```
feat(M03): 库存预占三态 lock/release/commit
fix(M06): 支付回调重复请求导致重复扣款
docs: 补齐报告书第 3 章用例规约
```

**为什么较真这一条**：三个月后回头查"这个数是哪次改坏的"，`git log --oneline` 是唯一线索。写成"改了一下""update"的人，最后都要靠翻代码猜。

---

## 5. 每天的动作（四步，别跳）

```bash
git pull --rebase                    # 1. 先把别人的拉下来
git checkout -b feature/T-C05-xxx    # 2. 开自己的分支（第一天做）
# …… 干活 ……
git add -A
git commit -m "feat(M06): 订单状态机表驱动"   # 3. 小步提交，一天可多次
git push                             # 4. 下班前推上去
```

**为什么每天都要推**：不是为了备份，是为了让别人看到你的进度。卡住了没人知道，最后一周才发现来不及——这是小组作业最常见的死法。

### 谁 Review 谁

| 改动涉及 | 必须找谁看 |
|---|---|
| M01 / M13 权限与系统 | A |
| M02 / M03 商品库存 | B |
| M06 / M07 / M08 订单与支付 | **C（关键路径，必须看）** |
| M04 / M05 会员与促销 | D |
| M11 / M10 前端两个端 | E |
| M09 / M12 报表与管理端 | F |
| 接口签名有任何变化 | **所有人**（W2 冻结后尤其如此） |

---

## 6. 五条禁令

1. **不直接 push `main`**。分支保护会拒你，别去想办法绕。
2. **不 push 构建产物**。`target/`、`node_modules/`、`dist/` 已在 `.gitignore` 里挡住，**不要用 `git add -f` 强制加**。仓库一旦被几十兆垃圾撑大，清不掉。
3. **不 force push 别人拉过的分支**。`git push -f` 会把别人的提交抹掉且无法恢复。只在自己独占的分支上用，且提前在群里说一声。
4. **密钥、数据库密码不入库**。本地配置写在 `application-local.yml`（已忽略）。**历史上已经提交过的密码，改文件是没用的，必须换密码**——这一点答辩时也可能被问。
5. **W2 末接口冻结后，改接口必须先打招呼**。改一个字段，可能同时打断三个人的进度。

---

## 7. 冲突了怎么办

```bash
git pull --rebase
# 提示 CONFLICT → 打开冲突文件，保留两边该留的内容，删掉 <<<<<<< ======= >>>>>>> 标记
git add <冲突的文件>
git rebase --continue
```

- 拿不准就 `git rebase --abort`，回到冲突前的状态，找那个模块的负责人一起看。**别硬猜着合并。**
- **永远不要** `git checkout -- .` 或 `git reset --hard` 来"清干净"——这会连你自己没提交的活一起删掉，且找不回来。
- 同一个文件被两个人同时大改，说明分工本身有问题，应该停下来对一下接口，而不是比谁手快。

---

## 8. 关键节点打标签

三个节点必须打 tag，答辩时要拿这个证明进度是逐步做出来的：

```bash
git tag -a v0.4-checkout -m "W4 末：完整收通一笔钱"      # 第 4 周末
git tag -a v0.6-freeze   -m "W6 末：功能冻结"            # 第 6 周末
git tag -a v1.0-demo     -m "答辩版"                     # 答辩前一天
git push --tags
```

**第 4 周那个 tag 是硬卡点**：打不上，就按 `00-团队执行手册.md` 里的止损方案砍功能，别拖到第 6 周才承认。

---

## 9. 常用命令速查

```bash
git status                       # 我现在改了啥
git log --oneline --graph -15     # 最近 15 条提交长什么样
git branch -a                    # 有哪些分支
git diff                         # 具体改了哪几行
git stash                        # 临时收起没写完的改动
git stash pop                    # 再拿出来
git restore <文件>                # 单个文件放弃修改（慎用）
```

---

## 10. 给组员的第一条消息（可直接转发）

> 仓库地址：`git@github.com:zgong545-eng/supermarket-pos.git`（公开库，组员点 Fork 或在群里要 Collaborator 权限）
> 因为公开，**任何口令、`.env`、私钥都不能提交**——`.gitignore` 已经挡掉，提交前仍然要自己看一眼 `git status`
>
> 1. 装 Git，然后 `git clone` 上面的地址
> 2. 如果 clone 卡住不动，看 `docs/03-Git协作约定.md` 第 2 节的 443 端口配置
> 3. 先读 `docs/超市收银管理系统-设计与分工说明.docx`（不懂技术也能看懂），知道自己负责哪块
> 4. 再读 `docs/00-团队执行手册.md` 里属于你的那部分任务
> 5. 分支名用 `feature/T-XXnn-简述`，任务号在手册里
