# 接入一个项目

1. 在控制台创建 Project，并选择适合的形态模板。
2. 绑定每个代码仓的本地路径及角色（`admin`、`app` 或 `other`）。
3. 为 `test`、`gray`、`prod` 分别绑定只读 Tool Instance；生产必须使用只读账号。
4. 启用通用 Skill、形态模板和项目覆盖 Skill。
5. 用一个已知历史故障验证报告是否包含可核验的证据。

## Demo App 样例

Project：`demo-app`

| Repo | 角色 |
|---|---|
| `/Users/yourname/IdeaProjects/workspaces/demo-admin-broad` | admin |
| `/Users/yourname/IdeaProjects/workspaces/demo-app-broad` | app |

不要在平台中保存生产写账号、Nacos 写凭据或可删除缓存的 Redis 凭据。

## 第二项目验收演练

以任意独立产品作为第二个 Project：仅创建项目、绑定代码仓、为 test 环境绑定一个 MySQL 只读实例并启用 `http-5xx` Skill。若不改 Harness 源码即可发起一次排查会话，即满足多项目接入验收。
